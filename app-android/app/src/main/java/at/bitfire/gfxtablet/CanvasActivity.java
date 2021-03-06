package at.bitfire.gfxtablet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

public class CanvasActivity
extends AppCompatActivity
implements View.OnSystemUiVisibilityChangeListener,
           SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final int RESULT_LOAD_IMAGE = 1;
    private static final String TAG = "GfxTablet.Canvas";
    private static final String FRAGMENT_CANVAS = "CanvasFragment";

    final Uri homepageUri = Uri.parse(("https://gfxtablet.bitfire.at"));

    NetworkViewModel networkModel;
    SharedPreferences preferences;
    boolean fullScreen = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        setContentView(R.layout.activity_canvas);

        networkModel = new ViewModelProvider(this).get(NetworkViewModel.class);
        networkModel.getHostName().setValue(preferences.getString(SettingsActivity.KEY_PREF_HOST, "unknown.invalid"));

        // update the UI when the connection state changes
        Observer<Boolean> connectedStateObserver = this::connectionAttempted;
        networkModel.getConnectedState().observe(this, connectedStateObserver);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (preferences.getBoolean(SettingsActivity.KEY_KEEP_DISPLAY_ACTIVE, true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_canvas, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (fullScreen)
            switchFullScreen(null);
        else
            super.onBackPressed();
    }

    public void showAbout(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, homepageUri));
    }

    public void showDonate(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, homepageUri.buildUpon().appendPath("donate").build()));
    }

    public void showSettings(MenuItem item) {
        startActivityForResult(new Intent(this, SettingsActivity.class), 0);
    }


    // preferences were changed

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case SettingsActivity.KEY_PREF_HOST:
                Log.i(TAG, "Recipient host changed, reconfiguring network client");
                String hostName = sharedPreferences.getString(SettingsActivity.KEY_PREF_HOST, "unknown.invalid");
                networkModel.getHostName().setValue(hostName);
                break;
        }
    }


    // full-screen methods

    public void switchFullScreen(MenuItem item) {
        final View decorView = getWindow().getDecorView();
        int uiFlags = decorView.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= 14)
            uiFlags ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 16)
            uiFlags ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 19)
            uiFlags ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        decorView.setOnSystemUiVisibilityChangeListener(this);
        decorView.setSystemUiVisibility(uiFlags);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        Log.i("GfxTablet", "System UI changed " + visibility);

        fullScreen = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;

        // show/hide action bar according to full-screen mode
        if (fullScreen) {
            getSupportActionBar().hide();
            Toast.makeText(this, R.string.leave_fullscreen, Toast.LENGTH_LONG).show();
        } else
            getSupportActionBar().show();
    }


    // template image logic

    public void setTemplateImage(MenuItem item) {
	    PopupMenu popup = new PopupMenu(this, findViewById(R.id.menu_set_template_image));
	    popup.getMenuInflater().inflate(R.menu.set_template_image, popup.getMenu());
	    popup.getMenu().findItem(R.id.menu_use_computer_screen).setChecked(preferences.getBoolean(SettingsActivity.KEY_SHOW_PC_SCREEN, false));
	    popup.show();
    }

    public void selectTemplateImage(MenuItem item) {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
        } else {
            i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(i, RESULT_LOAD_IMAGE);
    }

    public void clearTemplateImage(MenuItem item) {
        preferences.edit().remove(SettingsActivity.KEY_TEMPLATE_IMAGE).apply();
    }

    public void toggleComputerScreen(MenuItem item) {
    	boolean previousState = item.isChecked();
    	preferences.edit().putBoolean(SettingsActivity.KEY_SHOW_PC_SCREEN, !previousState).apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int flagsToPersist = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(selectedImage, flagsToPersist);
                preferences.edit().putString(SettingsActivity.KEY_TEMPLATE_IMAGE, selectedImage.toString()).apply();
            } else {
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                try (Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null)) {
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String picturePath = cursor.getString(columnIndex);

                    preferences.edit().putString(SettingsActivity.KEY_TEMPLATE_IMAGE, picturePath).apply();
                }
            }
        }
    }

    private void connectionAttempted(boolean success)
    {
        if (success)
            Toast.makeText(this, getText(R.string.send_confirmation) + networkModel.getNetClient().destAddress.getHostAddress() + ":" + NetworkClient.GFXTABLET_PORT, Toast.LENGTH_LONG).show();

        findViewById(R.id.canvas_message).setVisibility(success ? View.GONE : View.VISIBLE);

        // show canvas fragment on success, hide it otherwise
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment canvasFragment;
        if (success)
        {
            canvasFragment = new CanvasFragment();
            ft.replace(R.id.root, canvasFragment, FRAGMENT_CANVAS);
        }
        else
        {
            canvasFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_CANVAS);
            if (canvasFragment != null)
                ft.remove(canvasFragment);
        }
        ft.commitAllowingStateLoss();
    }

}
