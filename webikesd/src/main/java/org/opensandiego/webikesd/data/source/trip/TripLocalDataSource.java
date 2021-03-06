/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensandiego.webikesd.data.source.trip;

import android.support.annotation.NonNull;

import org.opensandiego.webikesd.data.model.Trip;
import org.opensandiego.webikesd.data.roomdb.TripDao;
import org.opensandiego.webikesd.data.source.LocalDataSource;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Concrete implementation of a data source as a db.
 */
@Singleton
class TripLocalDataSource extends LocalDataSource<Trip> implements TripDataSource {

  @NonNull
  private final TripDao mTripDao;

  @Inject
  TripLocalDataSource(@NonNull TripDao tripDao) {
    super(tripDao);
    this.mTripDao = tripDao;
  }

}
