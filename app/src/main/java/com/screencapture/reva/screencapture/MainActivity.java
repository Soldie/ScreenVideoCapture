package com.screencapture.reva.screencapture;


import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.FileProvider;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity  {
    private final String TAG = this.getClass().getSimpleName();
    private static final String filePath_KEY = "filePath_KEY";
    private int FRAME_RATE = 4;
    private static final int PERMISSION_CODE = 1;
    private int DISPLAY_WIDTH,DISPLAY_HEIGHT;
    private int hasPermissionStorage;
    private int hasPermissionAudio;
    private int mScreenDensity;
    private static final int REQUEST_CODE_SOME_FEATURES_PERMISSIONS = 20;
    private List<String> permissions = new ArrayList<>();
    private String filePath = "";

    @BindView(R.id.mainBrowser) WebView main_browser;
    @BindView(R.id.video_filepath) TextView tv_filepath;
    @BindView(R.id.button_rec) ToggleButton mToggleButton;
    @BindView(R.id.button_play_video) Button playButton;
    @BindView(R.id.button_share_video) Button shareButton;
    @BindView(R.id.button_del_video) Button delButton;

    private MediaRecorder mMediaRecorder;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (savedInstanceState!=null) {
            filePath = savedInstanceState.getString(filePath_KEY);
        }

        ShowHideButtons();
        main_browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        main_browser.loadUrl(getString(R.string.begin_url));
        mMediaRecorder = new MediaRecorder();
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaProjectionCallback = new MediaProjectionCallback();
        ScreenDimensions();
        GrantPermissions();

        ShowHideButtons();
    }

    /**
     * Клик на кнопках СТАРТ/СТОП или Play
     * @param view - объект кнопки на которую кликнули
     */
    @OnClick({R.id.button_rec,R.id.button_play_video,R.id.button_share_video,R.id.button_del_video})
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.button_rec:
                //включить/выключить запись
                    StartStopRecording((ToggleButton) view);
                    break;
            case R.id.button_play_video:
                //посмотреть видео
                    PlayVideo();
                    break;
            case R.id.button_share_video:
                //поделиться видео
                    ShareVideo();
                    break;
            case R.id.button_del_video:
                //удалить файл
                    DeleteFile();
                    break;
        }
    }

    /**
     * Клик на Toggle кнопке: включить/выключить запись экрана
     * @param view
     */
    private void StartStopRecording(ToggleButton view) {
        ToggleButton mToggleButton = view;
        Context context = getApplicationContext();
        if (mToggleButton.isChecked()) {
            startRecord();
        } else {
            stopRecord();
        }
    }

    /**
     * Посмотреть видео
     */
    private void PlayVideo() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(filePath));
        intent.setDataAndType(Uri.parse(filePath), "video/mp4");
        startActivity(intent);
    }

    /**
     * Расшарить видео
     */
    private void ShareVideo() {
        try {
            File file = new File(filePath);
            Uri contentUri = FileProvider.getUriForFile(getApplicationContext(), "com.screencapture.reva.screencapture.fileprovider", file);
            grantUriPermission("com.screencapture.reva.screencapture.fileprovider", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setDataAndType(contentUri, "video/mp4");
            //shareIntent.setData(contentUri);

            //shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "subj");
            //shareIntent.putExtra(android.content.Intent.EXTRA_TITLE, "title");
            //shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);

            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
        }
        catch (Exception e)
        {
            Toast.makeText(getApplicationContext(),e.getClass().toString() + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Удалить файл и выставить кнопки управления соответственно
     */
    private void DeleteFile() {


        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage( String.format(getString(R.string.DeleteQuestion),filePath))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean result = (new File(filePath)).delete();
                        if (result)
                        {
                            filePath="";
                            ShowHideButtons();
                        }
                    }

                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /**
     * Начинаем запись, уберём кнопки play и share, сгенерим имя видео
     */
    private void startRecord()
    {
        Context context = getApplicationContext();

        //сгенерим путь и новое имя для видеофайла
        generateFilePath();

        tv_filepath.setText(filePath);

        //инициализация
        initRecorder();
        prepareRecorder();

        //хитрый трюк ради разрешения на запись экрана
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
            return;
        }

        try {
            mVirtualDisplay = createVirtualDisplay();
            mMediaRecorder.start();   // Recording is now started
        }
        catch (Exception e)
        {
            mToggleButton.setChecked(false);
            Toast.makeText(context,e.getClass().toString() + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

    }


    private void generateFilePath() {
        Context context = getApplicationContext();

        //пусть будет корень, для тестовой разработки подойдёт
        //String path = Environment.getExternalStoragePublicDirectory(getString(R.string.app_storage_subfolder)).getPath();
        String path = context.getFilesDir().getPath() + File.separator + getString(R.string.app_storage_subfolder);

        File dir = new File(path);
        if(!dir.exists()) dir.mkdirs();
        if(!dir.exists())
        {
            Toast.makeText(context,path + " does not exists", Toast.LENGTH_LONG).show();
            return;
        }

        //название файла
        Calendar c = Calendar.getInstance();
        String mName = String.valueOf(c.get(Calendar.DATE))
                +"_"+String.valueOf(c.get(Calendar.MONTH))
                +"_"+String.valueOf(c.get(Calendar.YEAR))
                +"-"+String.valueOf(c.get(Calendar.HOUR_OF_DAY))
                +"_"+String.valueOf(c.get(Calendar.MINUTE))
                +"_"+String.valueOf(c.get(Calendar.SECOND))
                +".mp4";
        filePath = path+File.separator+mName;
    }

    /**
     * Инициализация, пока что все параметры в лоб, в реальности их надо хранить в каких нибудь Preferences но пока пусть будет так
     *
     */
    private void initRecorder() {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        String bitRate= getString(R.string.video_bit_rate);
        mMediaRecorder.setVideoEncodingBitRate(Integer.parseInt(bitRate));
        mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        mMediaRecorder.setVideoFrameRate(FRAME_RATE);

        mMediaRecorder.setOutputFile(filePath);
    }

    /**
     * Необходимое состояние MediaRecorder перед стартом записи, в отдельный метод ради try/catch
     */
    private void prepareRecorder() {
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            Log.e(TAG,e.getMessage());
            Toast.makeText(this,e.getClass().toString() + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Выясним текущие размеры экрана, в дальнейшем это может влиять на bitrate и framerate если экраны большие и будут сильно увеличивать размер видео
     */
    private void ScreenDimensions() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        DISPLAY_WIDTH = size.x;
        DISPLAY_HEIGHT = size.y;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
    }

    /**
     * Остановить запись, в случае неудачи удаляем файлик
     */
    private void stopRecord()
    {
        Context context = getApplicationContext();
        Toast.makeText(context, getString(R.string.stop_rec_message), Toast.LENGTH_SHORT).show();

        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            addVideoToGallery();
            ShowHideButtons();
        }
        catch (Exception e)
        {
            Toast.makeText(context,e.getClass().toString() + "\n" +  e.getMessage(), Toast.LENGTH_LONG).show();
            new File(filePath).delete();
        }
        finally {
            mMediaRecorder.release();
        }
    }

    /**
     * На всякий случай проверим необходимые разрешения
     */
    private void GrantPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            hasPermissionStorage = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            hasPermissionAudio = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);

            if( hasPermissionAudio != PackageManager.PERMISSION_GRANTED ) {
                permissions.add( Manifest.permission.RECORD_AUDIO );
            }

            if( hasPermissionStorage != PackageManager.PERMISSION_GRANTED ) {
                permissions.add( Manifest.permission.WRITE_EXTERNAL_STORAGE );
            }

            if( !permissions.isEmpty() ) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray( new String[permissions.size()] ), REQUEST_CODE_SOME_FEATURES_PERMISSIONS );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int grantResults[]) {
        switch (requestCode) {
            case REQUEST_CODE_SOME_FEATURES_PERMISSIONS: {
                for( int i = 0; i < permissions.length; i++ ) {
                    if( grantResults[i] == PackageManager.PERMISSION_GRANTED ) {
                        Log.d("Permissions", "Permission Granted: " + permissions[i] );
                    } else if( grantResults[i] == PackageManager.PERMISSION_DENIED ) {
                        Log.d("Permissions", "Permission Denied: " + permissions[i] );
                        finish();
                    }
                }
            }
            break;
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    /**
     * При закрытии отпустим объекты
     */
    @Override
    public void onDestroy() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        super.onDestroy();
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
                initRecorder();
                prepareRecorder();
            }
            mMediaProjection = null;
            stopRecord();
            Log.i(TAG, "MediaProjection Stopped");
        }
    }

    /**
     * добавим видео в галлерею
     */
    private void addVideoToGallery() {
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        File f = new File(filePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    /**
     * виртуальный экран
     * @return
     */
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay(TAG,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.screen_cast_permission_denied_error, Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            mMediaRecorder.reset();
            tv_filepath.setText("");
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(filePath_KEY, filePath);
    }

    /**
     * Скрыть или показать кнопки управления видеофайлом
     */
    protected void ShowHideButtons()
    {
        int VisibilityState;
        if (filePath=="")
        {
            VisibilityState = View.INVISIBLE;
        }
        else
        {
            VisibilityState = View.VISIBLE;
        }

        playButton.setVisibility(VisibilityState);
        shareButton.setVisibility(VisibilityState);
        delButton.setVisibility(VisibilityState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState!=null) {
            filePath = savedInstanceState.getString(filePath_KEY);
        }
        tv_filepath.setText(filePath);
        ShowHideButtons();
    }
}

