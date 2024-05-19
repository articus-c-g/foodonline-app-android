package ru.articus.foodonline;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {

    public static final int DB_VERSION = 1;
    public static final String DB_NAME = "database";
    public static final String MAIN_TABLE = "mainTable";
    public static final String KEY_ID = "_id";
    public static final String KEY_DATA = "data";
    public static final String KEY_NAME = "name";

    public DBHelper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + MAIN_TABLE + "(" + KEY_ID
                + " integer PRIMARY KEY," + KEY_DATA + " text," + KEY_NAME + " text" +")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists " + MAIN_TABLE);
        onCreate(db);
    }

}
