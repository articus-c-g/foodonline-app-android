package ru.articus.foodonline;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ServiceLoader;

public class MainActivity extends AppCompatActivity {

    private static WebView view;
    private Boolean settings = false; // Костыль, чтобы отслеживать отправлен ли пользователь в настройки

    DBHelper dbHelper;

    private static String CHANNEL_ID = "foodOnline";
    private static final int PERMISSIONS_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new DBHelper(this);

        JavaScriptInterface javaScriptInterface = new JavaScriptInterface(getApplicationContext());

        if(javaScriptInterface.loadJson("location") != null)  // Если есть запись о геолокации пользвателя
        {
            loadWebView(); // То запускаем webview
        }else{
            RequestPermissionGPS(); // Запрашиваем разрешение на получение геолокации
                                    // Если запретили геолокацию навсегда,
                                    // то запрос на резерешение всё равно сработает и
                                    // автоматом запустит webview
        }
        saveLocation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    }

    @Override
    //Когда пользователь вернулся с настрек, то срабатывает этот метод
    protected void onStart() {
        super.onStart();
        if(settings)
        {
            LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

            //Если включил GPS, то записываем gps
            if(isGPSEnabled)
                saveLocation();

            loadWebView();

            settings = false;
        }
    }

    private void loadWebView()
    {

        this.view = (WebView) findViewById(R.id.webView);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.addJavascriptInterface(new JavaScriptInterface(this), "webView_Storage");

        view.loadUrl("https://app.foodonline.pro/");
        //https://app.foodonline.pro/
        //http://df.dev.limonka.site/foodonline_app/

        WebViewClient webViewClient = new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView webview, String url)
            {
                if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("smsto:") || url.startsWith("mms:") || url.startsWith("mmsto:"))
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        };
        view.setWebViewClient(webViewClient);
    }

    public static void Reload(){
        view.post(new Runnable() {
            @Override
            public void run() {
                view.loadUrl("https://app.foodonline.pro/");
            }
        });
    }

    public void saveLocation()
    {
        LocationService locationService = new LocationService(getApplicationContext());
        JavaScriptInterface javaScriptInterface = new JavaScriptInterface(getApplicationContext());
        Location location = locationService.getLocation();
        if(location != null)
        {
            final char kv = (char) 34;
            double lat = location.getLatitude();
            double lon = location.getLongitude();

            javaScriptInterface.saveJson("{"+ kv +"latitude"+ kv +":" + lat + ","+ kv +"longitude"+ kv +":" + lon + "}", "location");
        }
    }

    private void RequestPermissionGPS() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION }, 1);
    }

    @Override
    public void onBackPressed() {
        if (view.canGoBack()) {
            view.goBack();
        } else {
            QuitDialog();
        }
    }

    private void QuitDialog()
    {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из приложения?")
                .setNegativeButton("Нет", null)
                .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        MainActivity.super.onBackPressed();
                    }
                }).create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        //Проверяем разрешил ли пользователь использовать GPS
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case PERMISSIONS_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // <--- Вот тут проверяем

                    LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                    boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

                    if (!isGPSEnabled) { //Если разрешил, но gps отключён, отправляем пользователя в настройки, чтобы он включил gps
                        settings = true;
                        Intent turnOnGPS = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        MainActivity.this.startActivity(turnOnGPS);
                    } else { //Если разрешил и gps включён, то записываем геолокацию и запускаем webview
                        saveLocation();
                        loadWebView();
                    }
                } else { // Если не разрешил, то просто запускаем webview
                    loadWebView();
                }
                break;
            default:
                break;

        }
    }

    public class JavaScriptInterface {
        Context mContext;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        Cursor cr;
        final char kv = (char) 34;
        Boolean isOld;

        JavaScriptInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public String getFirebaseToken() {
            try {
                Task task = FCMService.getToken(mContext);
                Tasks.await(task);
                return task.getResult().toString();

            }catch (Exception e) {
                 Log.e("Token:ERROR", "" + e.getMessage());
            }
            return null;
        }

        @JavascriptInterface
        public void saveJson(String getJson, String nameKey) {
            cv.put(DBHelper.KEY_DATA, getJson);
            cv.put(DBHelper.KEY_NAME, nameKey);
            try {
                cr = db.rawQuery("SELECT * FROM mainTable WHERE name = " + kv  + nameKey + kv, null);
                isOld = cr.getCount() > 0;
            } catch (Exception e) {
                Log.e("SQLiteRawQuery", e.toString());
            }

            if (!isOld) {
                db.insert(DBHelper.MAIN_TABLE, null, cv);
            } else {
                db.update(DBHelper.MAIN_TABLE, cv, "name = " + kv + nameKey + kv, null);
            }
            cr.close();
        }

        @JavascriptInterface
        public String loadJson(String nameKey) {
            cr = db.query(DBHelper.MAIN_TABLE, null, "name = " + kv + nameKey + kv, null, null, null, null);
            cr.moveToFirst();
            if(cr.getCount() != 0) {
                return cr.getString(1);
            }
            else{return null;}

        }

        @JavascriptInterface
        public String loadString(String nameKey) {
            return loadJson(nameKey);
        }

        @JavascriptInterface
        public void saveString(String getString, String nameKey) {
            saveJson(getString, nameKey);
        }

        @JavascriptInterface
        public void saveBool(Boolean getBool ,String nameKey){
            if(getBool){
                saveJson("1", nameKey);
            }else {
                saveJson("0", nameKey);
            }
        }

        @JavascriptInterface
        public Boolean loadBool(String nameKey){
            String b =  loadJson(nameKey);
            if(b.equals("1")){
                return true;
            }else if (b.equals("0")){
                return false;
            }
            return null;
        }

        @JavascriptInterface
        public String getLocation(){

            LocationService locationService = new LocationService(getApplicationContext());
            Location location = locationService.getLocation();

            if(location != null)
            {
                final char kv = (char) 34;
                double lat = location.getLatitude();
                double lon = location.getLongitude();

                return "{"+ kv +"latitude"+ kv +":" + lat + ","+ kv +"longitude"+ kv +":" + lon + "}";
            }
            return null;
        }

        @JavascriptInterface
        public void removeItem(String item){
            if(item != null)
                db.delete(DBHelper.MAIN_TABLE, "name = " + kv + item + kv, null);
        }
    }
}