package com.example.retrofitcache.http;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import com.example.retrofitcache.MyApplication;
import com.example.retrofitcache.R;
import com.example.retrofitcache.constants.HttpConstants;
import com.example.retrofitcache.util.ApkUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.example.retrofitcache.http.HttpRequests.CONNECT_TIME_OUT;
import static com.example.retrofitcache.http.HttpRequests.READ_TIME_OUT;

/**
 * Created by Administrator on 2017/4/26.
 */

public class FileDownloadIntentService extends IntentService {

    public static final String DOWNLOAD_FILE_URI = "DOWNLOAD_FILE_URI";

    private boolean isLoading;
    private int preProgress = 0;
    private int NOTIFY_ID = 1000;
    private NotificationCompat.Builder builder;
    private NotificationManager notificationManager;

    private Context mContext;
    private File mDownloadedFile;

    private static final int UPDATE_PERCENT_THRESHOLD = 1;

    /**
     * 目标文件存储的文件名
     */
    private String destFileName = System.currentTimeMillis() + "yzzc.apk";

    //storage/emulated/0/Download/
    private static final String DOWNLOAD_SAVE_PATH =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator;


    public FileDownloadIntentService() {
        super("FileDownloadService started");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mContext = this;
        if (intent == null) return;
        String fileUri = intent.getStringExtra(DOWNLOAD_FILE_URI);

        //开启Log
        HttpLoggingInterceptor logInterceptor = new HttpLoggingInterceptor();
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);

        //手动创建一个OkHttpClient并设置超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .readTimeout(READ_TIME_OUT, TimeUnit.MILLISECONDS)
                .connectTimeout(CONNECT_TIME_OUT,TimeUnit.MILLISECONDS)
                .addInterceptor(logInterceptor)
                .build();

        // don't use ServiceGenerator since it adds a logging interceptor,
        // which might break the async progress
        Retrofit retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(HttpConstants.BASE_URL)
                .build();

        Call<ResponseBody> request = retrofit
                .create(HttpService.class)
                .downloadFileWithDynamicUrlAsync(fileUri);
        loadFile(request);
    }

    /**
     * 下载文件
     */
    private void loadFile(Call<ResponseBody> request) {

        //是否在更新
        if (isLoading) {
            Toast.makeText(mContext, "正在后台下载中,点击通知栏查看安装。", Toast.LENGTH_LONG).show();
            return;
        }
        isLoading = true;//下载中
        initNotification();
        try {
            writeResponseBodyToDisk(request.execute().body());
        } catch (IOException e) {
            isLoading = false;//下载失败，可重复下载
            cancelNotification();
            Log.e(
                    "FileDownloadIntentSvc",
                    "IOException while download file:" + e.getMessage()
            );
            // todo notify & update UI
        }
    }

    /**
     * 写入文件
     * @param body
     * @throws IOException
     */
    private void writeResponseBodyToDisk(ResponseBody body) throws IOException {
        // todo change the file location/name according to your needs
        mDownloadedFile = new File(DOWNLOAD_SAVE_PATH, destFileName);

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            byte[] fileReader = new byte[4096];

            // set fixed file size
            long fileSize = body.contentLength();

            // open streams
            inputStream = new BufferedInputStream(body.byteStream(), 4096);
            outputStream = new FileOutputStream(mDownloadedFile);

            // set variables through the buffered reading
            int readBuffer;
            long fileSizeDownloadedInByte = 0;
            int fileDownloadedInPercentage = 0;

            while (true) {
                readBuffer = inputStream.read(fileReader);

                if (readBuffer == -1) {
                    break;
                }

                fileSizeDownloadedInByte += readBuffer;

                // update UI for every percent downloaded (you might want to change that depending on your file size)
                int newfileDownloadedInPercentage = (int) ((fileSizeDownloadedInByte * 100) / fileSize);
                if (fileDownloadedInPercentage + UPDATE_PERCENT_THRESHOLD <= newfileDownloadedInPercentage) {
                    fileDownloadedInPercentage = newfileDownloadedInPercentage;
                    sendUpdateUiIntent(fileDownloadedInPercentage, fileSize);
                }

                outputStream.write(fileReader, 0, readBuffer);
            }

            // completely downloaded! send final update to UI
            sendUpdateUiIntent(100, fileSize);

            outputStream.flush();
        } catch (IOException e) {
            // failed! send update to the UI
            sendUpdateUiIntent(0, 0);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (outputStream != null) {
                outputStream.close();
            }
        }
    }


    /**
     * 发送进度
     * @param fileDownloadedInPercent
     * @param fileSize
     */
    private void sendUpdateUiIntent(int fileDownloadedInPercent, long fileSize) {
        Message msg = handler.obtainMessage();
        msg.arg1 = fileDownloadedInPercent;
        msg.obj = fileSize;
        handler.sendMessage(msg);

    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int arg1 = msg.arg1;
            if (arg1 == 100) {
                isLoading = false;//下载完成，可重复下载
                onDownloadComplete(mDownloadedFile);
            } else {
                updateNotification(arg1);

            }
        }
    };


    /**
     * 初始化Notification通知
     */
    public void initNotification() {
        builder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText("0%")
                .setContentTitle("新版本更新")
                .setProgress(100, 0, false);
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_ID, builder.build());
    }

    /**
     * 更新通知
     */
    public void updateNotification(int progress) {
        int currProgress = progress;
        if (preProgress < currProgress) {
            builder.setContentText(progress + "%");
            builder.setProgress(100, progress, false);
            notificationManager.notify(NOTIFY_ID, builder.build());
        }
        preProgress = (int) progress;
    }

    /**
     * 取消通知
     */
    public void cancelNotification() {
        notificationManager.cancel(NOTIFY_ID);
    }


    private void onDownloadComplete(File file) {
        stopForeground(true);
        builder.mActions.clear();
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentText("下载完成,点击安装")
                .setContentIntent(getDefaultIntent(file))
                .setProgress(0, 0, false);
        notificationManager.notify(NOTIFY_ID, builder.build());

    }

    /**
     * 安装apk
     *
     * @param file
     * @return
     */
    private PendingIntent getDefaultIntent(File file) {
        Uri uri;
        //new File(DOWNLOAD_SAVE_PATH + File.separator + destFileName)
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", file);
        } else {
            uri = Uri.fromFile(file);
        }


        //弹出安装页面
        if (uri !=null){
            ApkUtils.install(MyApplication.context,uri);
        }else{
            if (file!=null){
                ApkUtils.install(MyApplication.context,file);
            }
        }

        //通知栏点击安装
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        return PendingIntent.getActivity(this, 1, intent, FLAG_UPDATE_CURRENT);
    }
}
