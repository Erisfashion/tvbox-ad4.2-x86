package com.github.tvbox.osc.bbox.base;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;
import com.github.tvbox.osc.bbox.bean.VodInfo;
import com.github.tvbox.osc.bbox.callback.EmptyCallback;
import com.github.tvbox.osc.bbox.callback.LoadingCallback;
import com.github.tvbox.osc.bbox.constant.URL;
import com.github.tvbox.osc.bbox.data.AppDataManager;
import com.github.tvbox.osc.bbox.server.ControlManager;
import com.github.tvbox.osc.bbox.util.*;
import com.github.tvbox.osc.bbox.util.js.JSEngine;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import com.xuexiang.xupdate.XUpdate;
import com.xuexiang.xupdate.entity.UpdateError;
import com.xuexiang.xupdate.listener.OnUpdateFailureListener;
import com.xuexiang.xupdate.utils.UpdateUtils;
import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.xuexiang.xupdate.entity.UpdateError.ERROR.CHECK_NO_NEW_VERSION;

/**
 * @author pj567
 * @date :2020/12/17
 * @description: 集成全局异常文件捕获与 Android 4.2.2 兼容优化
 */
public class App extends MultiDexApplication {

    private static App instance;
    private static P2PClass p;
    public static String burl;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 注册全局未捕获异常监听：将崩溃堆栈同步写入本地文件
        initCrashHandler();

        try {
            initParams();
        } catch (Throwable t) {
            LOG.e("initParams failed: " + t.getMessage());
        }

        try {
            OkGoHelper.init();
            EpgUtil.init();
        } catch (Throwable t) {
            LOG.e("Network helper init failed: " + t.getMessage());
        }

        try {
            ControlManager.init(this);
            LOG.i("Web服务器初始化完成！");
        } catch (Throwable t) {
            LOG.e("ControlManager init failed: " + t.getMessage());
        }

        try {
            AppDataManager.init();
        } catch (Throwable t) {
            LOG.e("AppDataManager init failed: " + t.getMessage());
        }

        try {
            LoadSir.beginBuilder()
                    .addCallback(new EmptyCallback())
                    .addCallback(new LoadingCallback())
                    .commit();
        } catch (Throwable t) {
            LOG.e("LoadSir init failed: " + t.getMessage());
        }

        try {
            AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                    .setSupportDP(false)
                    .setSupportSP(false)
                    .setSupportSubunits(Subunits.MM);
        } catch (Throwable t) {
            LOG.e("AutoSizeConfig init failed: " + t.getMessage());
        }

        try {
            PlayerHelper.init();
        } catch (Throwable t) {
            LOG.e("PlayerHelper init failed: " + t.getMessage());
        }

        try {
            JSEngine.getInstance().create();
        } catch (Throwable t) {
            LOG.e("JSEngine create failed: " + t.getMessage());
        }

        try {
            FileUtils.cleanPlayerCache();
        } catch (Throwable t) {
            LOG.e("cleanPlayerCache failed: " + t.getMessage());
        }

        try {
            initXUpdate();
        } catch (Throwable t) {
            LOG.e("XUpdate init failed: " + t.getMessage());
        }
    }

    /**
     * 全局崩溃拦截：自动把闪退日志写到外置卡根目录及应用内部目录
     */
    private void initCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                try {
                    // 1. 优先写入平板内部存储根目录 /sdcard/crash.txt
                    File sdcard = Environment.getExternalStorageDirectory();
                    if (sdcard != null && sdcard.exists()) {
                        File crashFile = new File(sdcard, "crash.txt");
                        PrintWriter pw = new PrintWriter(new FileWriter(crashFile, false));
                        ex.printStackTrace(pw);
                        pw.flush();
                        pw.close();
                    }

                    // 2. 双重备份：写入 /sdcard/Android/data/tv.org.eu.bunnyabc/files/crash.txt
                    File extFilesDir = getExternalFilesDir(null);
                    if (extFilesDir != null) {
                        File crashBackup = new File(extFilesDir, "crash.txt");
                        PrintWriter pw2 = new PrintWriter(new FileWriter(crashBackup, false));
                        ex.printStackTrace(pw2);
                        pw2.flush();
                        pw2.close();
                    }
                } catch (Throwable ignored) {
                }

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, ex);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
            }
        });
    }

    private void initXUpdate() {
        XUpdate.get()
            .debug(true)
            .isWifiOnly(false)
            .isGet(true)
            .isAutoMode(false)
            .param("versionCode", UpdateUtils.getVersionCode(this))
            .param("appKey", getPackageName())
            .setOnUpdateFailureListener(new OnUpdateFailureListener() {
                @Override
                public void onFailure(UpdateError error) {
                    error.printStackTrace();
                    if (error.getCode() != CHECK_NO_NEW_VERSION) {
                        Toast.makeText(getApplicationContext(), error.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .supportSilentInstall(false)
            .init(this);
    }

    private void initParams() {
        Hawk.init(this).build();

        putDefault(HawkConfig.DEBUG_OPEN, false);
        putDefault(HawkConfig.PLAY_TYPE, 1);
        putDefault(HawkConfig.HOME_REC, 1);
        putDefault(HawkConfig.PLAY_RENDER, 1);
        putDefault(HawkConfig.IJK_CODEC, "软解码");
        putDefault(HawkConfig.HOME_REC_STYLE, false);

        putDefault(HawkConfig.PROXY_URL, URL.DOMAIN_NAME_PROXY);
        putDefault(HawkConfig.LIVE_CHANNEL_REVERSE, true);
        putDefault(HawkConfig.LIVE_SHOW_TIME, true);
        putDefaultApis();
    }

    private void putDefaultApis() {
        String url = URL.DOMAIN_NAME_PROXY;

        List<String> proxyUrlHistory = Hawk.get(HawkConfig.PROXY_URL_HISTORY, new ArrayList<>());
        proxyUrlHistory.add(url);
        proxyUrlHistory.add("https://github.moeyy.xyz/");
        proxyUrlHistory.add("https://gh.ddlc.top/");
        proxyUrlHistory.add("https://ghps.cc/");
        proxyUrlHistory.add("https://raw.bunnylblbblbl.eu.org/");

        String defaultStoreApi = URL.DEFAULT_STORE_API_URL;
        putDefault(HawkConfig.DEFAULT_STORE_API, defaultStoreApi);
        putDefault(HawkConfig.PROXY_URL_HISTORY, proxyUrlHistory);
    }

    private void putDefault(String key, Object value) {
        if (!Hawk.contains(key)) {
            Hawk.put(key, value);
        }
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        try {
            JSEngine.getInstance().destroy();
        } catch (Throwable ignored) {
        }
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                File cacheDir = instance.getExternalCacheDir();
                String cachePath = (cacheDir != null) ? cacheDir.getAbsolutePath() : instance.getCacheDir().getAbsolutePath();
                p = new P2PClass(cachePath);
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    private VodInfo vodInfo;

    public void setVodInfo(VodInfo vodinfo) {
        this.vodInfo = vodinfo;
    }

    public VodInfo getVodInfo() {
        return this.vodInfo;
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    private static String dashData;

    public void setDashData(String data) {
        dashData = data;
    }

    public String getDashData() {
        return dashData;
    }
}
