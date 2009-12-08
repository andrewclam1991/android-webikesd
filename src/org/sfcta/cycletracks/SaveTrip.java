package org.sfcta.cycletracks;

import java.text.DateFormat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class SaveTrip extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.save);

        // User prefs btn
        final Button prefsButton = (Button) findViewById(R.id.ButtonPrefs);
        final Intent pi = new Intent(this, UserInfoActivity.class);
        prefsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(pi);
            }
        });

        SharedPreferences settings = getSharedPreferences("PREFS", 0);
        if (settings.getAll().size() >= 1) {
            prefsButton.setVisibility(View.GONE);
        }

		// Discard btn
		final Button btnDiscard = (Button) findViewById(R.id.ButtonDiscard);
		btnDiscard.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getBaseContext(), "Trip discarded.",	Toast.LENGTH_SHORT).show();

				cancelRecording();

				Intent i = new Intent(SaveTrip.this, MainInput.class);
				i.putExtra("keepme", true);
				startActivity(i);
				SaveTrip.this.finish();
			}
		});

		// Submit btn
		final Button btnSubmit = (Button) findViewById(R.id.ButtonSubmit);
		final Intent xi = new Intent(this, ShowMap.class);

		btnSubmit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Figure out which trip we're looking at
		        Bundle cmds = getIntent().getExtras();
	            long tripid = cmds.getLong("trip");
				TripData trip = TripData.fetchTrip(SaveTrip.this, tripid);
				trip.populateDetails();

				Toast.makeText(getBaseContext(),
				        "Submitting trip with "+trip.numpoints+" points. Thanks for using CycleTracks!",
				        Toast.LENGTH_SHORT).show();

				// Find user-entered info
				Spinner purpose = (Spinner) findViewById(R.id.SpinnerPurp);
				EditText notes = (EditText) findViewById(R.id.NotesField);

				String fancystarttime = DateFormat.getInstance().format(trip.startTime);

				// Save the trip details to the phone database. W00t!
				trip.updateTrip(
						purpose.getSelectedItem().toString(),
						fancystarttime,
						notes.getEditableText().toString());

				resetService();

				// Show the map!
                xi.putExtra("showtrip", trip.tripid);
                xi.putExtra("uploadTrip", true);
				startActivity(xi);
				SaveTrip.this.finish();
			}
		});
	}

	void cancelRecording() {
		Intent rService = new Intent(this, RecordingService.class);
		ServiceConnection sc = new ServiceConnection() {
			public void onServiceDisconnected(ComponentName name) {}
			public void onServiceConnected(ComponentName name, IBinder service) {
				IRecordService rs = (IRecordService) service;
				rs.cancelRecording();
			}
		};
		// This should block until the onServiceConnected (above) completes.
		bindService(rService, sc, Context.BIND_AUTO_CREATE);
	}

	void resetService() {
		Intent rService = new Intent(this, RecordingService.class);
		ServiceConnection sc = new ServiceConnection() {
			public void onServiceDisconnected(ComponentName name) {}
			public void onServiceConnected(ComponentName name, IBinder service) {
				IRecordService rs = (IRecordService) service;
				rs.reset();
			}
		};
		// This should block until the onServiceConnected (above) completes.
		bindService(rService, sc, Context.BIND_AUTO_CREATE);
	}
}
