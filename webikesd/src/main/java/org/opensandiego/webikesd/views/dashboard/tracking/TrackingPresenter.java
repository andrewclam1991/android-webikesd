package org.opensandiego.webikesd.views.dashboard.tracking;

import com.google.common.base.Optional;

import org.opensandiego.webikesd.data.model.CyclePoint;
import org.opensandiego.webikesd.data.model.Trip;
import org.opensandiego.webikesd.data.model.TripCyclePoint;
import org.opensandiego.webikesd.data.source.annotations.Repo;
import org.opensandiego.webikesd.data.source.cyclepoint.CyclePointDataSource;
import org.opensandiego.webikesd.data.source.trip.TripDataSource;
import org.opensandiego.webikesd.data.source.tripcyclepoint.TripCyclePointDataSource;
import org.opensandiego.webikesd.util.schedulers.BaseSchedulerProvider;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

@Singleton
class TrackingPresenter implements TrackingContract.Presenter {

  // Services (Invisible View)
  @Nullable
  private TrackingContract.Service mService;

  // Internal states
  @NonNull
  private TrackingContract.TripState mCurrentTripState;
  @NonNull
  private TrackingContract.TripState mNoTripTripState;
  @NonNull
  private TrackingContract.TripState mTripStartedTripState;
  @NonNull
  private TrackingContract.TripState mTripPausedTripState;

  // Data
  @NonNull
  private final TripCyclePointDataSource mTripCyclePtRepo;
  @NonNull
  private final TripDataSource mTripDataRepo;
  @NonNull
  private final CyclePointDataSource mCyclePtRepo;
  @NonNull
  private final BaseSchedulerProvider mSchedulerProvider;
  @NonNull
  private final CompositeDisposable mCompositeDisposable;
  @NonNull
  private final String mTripId;
  @Nullable
  private Trip mTrip;

  @Inject
  TrackingPresenter(@NonNull @Repo TripCyclePointDataSource tripCyclePtRepo,
                    @NonNull @Repo TripDataSource tripDataRepo,
                    @NonNull @Repo CyclePointDataSource cyclePtRepo,
                    @NonNull BaseSchedulerProvider schedulerProvider) {
    mTripCyclePtRepo = tripCyclePtRepo;
    mTripDataRepo = tripDataRepo;
    mCyclePtRepo = cyclePtRepo;
    mSchedulerProvider = schedulerProvider;
    mCompositeDisposable = new CompositeDisposable();
    mTripId = UUID.randomUUID().toString();

    // Setup internal states
    mNoTripTripState = new NoTripState();
    mTripStartedTripState = new TripStartedState();
    mTripPausedTripState = new TripPausedState();
    mCurrentTripState = mNoTripTripState;
  }

  @Override
  public void setView(TrackingContract.Service view) { mService = view; }

  @Override
  public void dropView() {
    mCompositeDisposable.clear();
    mService = null;
  }

  @Override
  public void loadTrip() {
    Disposable disposable = mTripDataRepo
        .get(mTripId)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .subscribeOn(mSchedulerProvider.io())
        .observeOn(mSchedulerProvider.ui())
        .subscribe(
            // onNext
            this::setTripData,
            // onError
            Throwable::printStackTrace
        );

    mCompositeDisposable.add(disposable);
  }

  private void setTripData(@NonNull Trip trip) {
    mTrip = trip;
  }

  @Override
  public void startTrip() { mCurrentTripState.startTrip(); }

  @Override
  public void updateTrip(double latitude, double longitude) {
    mCurrentTripState.updateTrip(latitude, longitude);
  }

  @Override
  public void pauseTrip() { mCurrentTripState.pauseTrip(); }

  @Override
  public void cancelTrip() { mCurrentTripState.cancelTrip(); }

  @Override
  public void completeTrip() { mCurrentTripState.completeTrip(); }

  /**
   * Concrete internal {@link TrackingContract.TripState} when there is no
   * trip or trip has stopped.
   */
  private class NoTripState implements TrackingContract.TripState {
    @Override
    public void startTrip() {
      Timber.d("startTrip() called, handle starting a new trip.");
      Timber.d("new trip id: %s", mTripId);

      // create and startTrip a new trip
      mTrip = new Trip(mTripId);

      Disposable disposable = mTripDataRepo
          .add(mTrip)
          .subscribeOn(mSchedulerProvider.io())
          .observeOn(mSchedulerProvider.ui())
          .subscribe(() -> {
            mCurrentTripState = mTripStartedTripState;
            if (mService == null || !mService.isActive()) { return; }
            mService.startLocationUpdates();
            Timber.d("new trip with id %s started successfully.", mTripId);
          }, Throwable::printStackTrace);

      // add to execution queue
      mCompositeDisposable.add(disposable);
    }

    @Override
    public void updateTrip(double latitude, double longitude) {
      Timber.d("updateTrip() called, invalid request, no trip to update.");
    }

    @Override
    public void pauseTrip() {
      Timber.d("pauseTrip() called, invalid request, no trip to pause.");
    }

    @Override
    public void cancelTrip() {
      Timber.d("cancelTrip() called, invalid request, no trip to cancel.");
    }

    @Override
    public void completeTrip() {
      Timber.d("completeTrip() called, invalid request, no trip to complete.");
    }
  }

  /**
   * Concrete internal {@link TrackingContract.TripState} when trip
   * is active and started
   */
  private class TripStartedState implements TrackingContract.TripState {
    @Nullable
    private CyclePoint mLastKnownCyclePoint;

    @Override
    public void startTrip() {
      Timber.d("startTrip() called, invalid request, trip already started.");
    }

    @Override
    public void updateTrip(double latitude, double longitude) {
      Timber.d("updateTrip() called, handle update.");

      // Create pt object from location data
      String id = UUID.randomUUID().toString();
      long timestamp = System.currentTimeMillis();
      CyclePoint currentPt = new CyclePoint(id, latitude, longitude, timestamp);

      if (mLastKnownCyclePoint == null) {
        // first pt, no speed
        currentPt.setSpeed(0);
      } else {
        // TODO calculate speed base on distance and time elapsed
        double elapsedTime = currentPt.getTime() - mLastKnownCyclePoint.getTime();
        currentPt.setSpeed(10);
      }

      // update last known cycle point with current
      mLastKnownCyclePoint = currentPt;

      // Create trip <-> association record
      String tripCyclePtUid = UUID.randomUUID().toString();
      TripCyclePoint tripCyclePt = new TripCyclePoint(tripCyclePtUid, mTripId, currentPt.getUid());

      // Update the cycle point and then add the trip <-> point association record
      Disposable disposable = mCyclePtRepo.add(mLastKnownCyclePoint)
          .andThen(mTripCyclePtRepo.add(tripCyclePt))
          .subscribeOn(mSchedulerProvider.io())
          .observeOn(mSchedulerProvider.io())
          .subscribe();

      // add to execution queue
      mCompositeDisposable.add(disposable);
    }

    @Override
    public void pauseTrip() {
      Timber.d("pauseTrip() called, change to paused state.");
      mCurrentTripState = mTripPausedTripState;
      if (mService == null || !mService.isActive()) { return; }
      mService.stopLocationUpdates();
    }

    @Override
    public void cancelTrip() {
      Timber.d("cancelTrip() called, change to paused state, delegate pause trip to new state.");
      mCurrentTripState = mTripPausedTripState;
      mCurrentTripState.cancelTrip();
    }

    @Override
    public void completeTrip() {
      Timber.d("completeTrip() called, change to paused state, delegate complete trip to new " +
          "state.");
      mCurrentTripState = mTripPausedTripState;
      mCurrentTripState.completeTrip();
    }
  }

  /**
   * Concrete internal {@link TrackingContract.TripState} when trip
   * is inactive and paused.
   */
  private class TripPausedState implements TrackingContract.TripState {

    @Override
    public void startTrip() {
      Timber.d("startTrip() called, handle restarting a paused trip.");
      mCurrentTripState = mTripStartedTripState;
      if (mService == null || !mService.isActive()) { return; }
      mService.startLocationUpdates();
    }

    @Override
    public void updateTrip(double latitude, double longitude) {
      Timber.d("updateTrip() called, invalid request, can't update a paused trip.");
    }

    @Override
    public void pauseTrip() {
      Timber.d("pauseTrip() called, invalid request, trip already paused.");
    }

    @Override
    public void cancelTrip() {
      Timber.d("cancelTrip() called, handle deleting instance trip and stop service upon complete" +
          ".");

      // cancel trip by deleting from data source
      Disposable disposable = mTripDataRepo
          .delete(mTripId)
          .subscribeOn(mSchedulerProvider.io())
          .observeOn(mSchedulerProvider.ui())
          .subscribe(() -> {
            if (mService == null || !mService.isActive()) { return; }
            mService.stopService();
            mCurrentTripState = mNoTripTripState;
          }, Throwable::printStackTrace);

      // add to execution queue
      mCompositeDisposable.add(disposable);
    }

    @Override
    public void completeTrip() {
      Timber.d("completeTrip() called, handle persisting instance trip and stop service upon complete.");

      // complete trip iff there is data to save
      if (mTrip != null) {
        Timber.d("has trip data to save, save trip id: %s", mTripId);

        mTrip.setEndTime(System.currentTimeMillis());
        Disposable disposable = mTripDataRepo
            .add(mTrip)
            .subscribeOn(mSchedulerProvider.io())
            .observeOn(mSchedulerProvider.ui())
            .subscribe(() -> {
              if (mService == null || !mService.isActive()) { return; }
              mService.stopService();
              mCurrentTripState = mNoTripTripState;
            }, Throwable::printStackTrace);

        // add to execution queue
        mCompositeDisposable.add(disposable);
      } else {
        // just stop the service
        Timber.d("no trip data to save.");
        if (mService == null || !mService.isActive()) { return; }
        mService.stopService();
        mCurrentTripState = mNoTripTripState;
      }
    }
  }
}