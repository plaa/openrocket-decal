package net.sf.openrocket.android;

import net.sf.openrocket.R;
import net.sf.openrocket.android.rocket.OpenRocketLoaderActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class Main extends OpenRocketLoaderActivity {

	public final static String restartFlag = "restart";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Intent i = getIntent();
		// Rocket already loaded.  go to viewer unless we want to restart.
		if ( ( i.getBooleanExtra(restartFlag, false) == false)  && CurrentRocketHolder.getCurrentRocket().getRocketDocument() != null ) {
			moveOnToViewer();
		}
		setContentView(R.layout.main);
		((Button) findViewById(R.id.main_open)).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Main.this.pickOrkFiles();
					}
				});
		((Button) findViewById(R.id.main_browse)).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						ActivityHelpers.browseMotors(Main.this);
					}
				});
		((Button) findViewById(R.id.main_donate)).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						ActivityHelpers.donate(Main.this);
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch( item.getItemId() ) {
		case R.id.main_menu_preferences:
			ActivityHelpers.startPreferences(this);
			return true;
		case R.id.menu_about:
			ActivityHelpers.showAbout(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
