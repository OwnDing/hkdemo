package com.zhite.zhite.service;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.zhite.zhite.common.osSelect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class HkClientCamera {
    // 注入日志记录器
    private static final Logger logger = LoggerFactory.getLogger(HkClientCamera.class);

    static HCNetSDK hCNetSDK = null;
    static PlayCtrl playControl = null;
    static FExceptionCallBack_Imp fExceptionCallBack;
    static class FExceptionCallBack_Imp implements HCNetSDK.FExceptionCallBack {
        public void invoke(int dwType, int lUserID, int lHandle, Pointer pUser) {
            System.out.println("异常事件类型:" + dwType);
            return;
        }
    }
    static int lPlayHandle = -1;

    private Map<String, Integer> clients = new HashMap<String, Integer>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int RETRY_DELAY_SECONDS = 5;

    /**
     * 动态库加载
     *
     * @return
     */
    private static boolean createSDKInstance() {
        if (hCNetSDK == null) {
            synchronized (HCNetSDK.class) {
                String strDllPath = "";
                try {
                    if (osSelect.isWindows()) {
//                        ClassPathResource resource = new ClassPathResource("\\lib\\HCNetSDK.dll");
//                        System.out.println("win系统加载库路径:" + resource.getFile().getAbsolutePath());
                        // win系统加载库路径
                        strDllPath = "C:\\Users\\Administrator\\Desktop\\hk\\hklib\\HCNetSDK.dll";
                    }

                    else if (osSelect.isLinux()) {
                        ClassPathResource resource = new ClassPathResource("/lib/hk/libhcnetsdk.so");
                        // Linux系统加载库路径
                        strDllPath = resource.getFile().getAbsolutePath();
                    }
                    hCNetSDK = (HCNetSDK) Native.loadLibrary(strDllPath, HCNetSDK.class);
                } catch (Exception ex) {
                    System.out.println("loadLibrary: " + strDllPath + " Error: " + ex.getMessage());
                    logger.error("loadLibrary: {} Error: {}", strDllPath, ex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 播放库加载
     *
     * @return
     */
    private static boolean createPlayInstance() {
        if (playControl == null) {
            synchronized (PlayCtrl.class) {
                String strPlayPath = "";
                try {
                    if (osSelect.isWindows())
                        // win系统加载库路径
                        strPlayPath = "C:\\Users\\Administrator\\Desktop\\hk\\hklib\\PlayCtrl.dll";
                    else if (osSelect.isLinux())
                        // Linux系统加载库路径
                        strPlayPath = System.getProperty("user.dir") + "/lib/libPlayCtrl.so";
                    playControl = (PlayCtrl)Native.loadLibrary(strPlayPath,PlayCtrl.class);

                } catch (Exception ex) {
                    System.out.println("loadLibrary: " + strPlayPath + " Error: " + ex.getMessage());
                    logger.error("loadLibrary: {} Error: {}", strPlayPath, ex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    public HkClientCamera() {
        logger.info("HK Camera is initialized");
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("HK Camera Error: {}", e.getMessage());
        }
    }

    public void init() {
        if (hCNetSDK == null && playControl==null) {
            if (!createSDKInstance()) {
                System.out.println("Load SDK fail");
                logger.error("Load SDK fail");
                return;
            }
            if (!createPlayInstance()) {
                System.out.println("Load PlayCtrl fail");
                logger.error("Load PlayCtrl fail");
                return;
            }
        }
        //linux系统建议调用以下接口加载组件库
        if (osSelect.isLinux()) {
            HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
            HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);
            //这里是库的绝对路径，请根据实际情况修改，注意改路径必须有访问权限
            String strPath1 = System.getProperty("user.dir") + "/src/main/resources/lib/hk/libcrypto.so.1.1";
            String strPath2 = System.getProperty("user.dir") + "/src/main/resources/lib/hk/libssl.so.1.1";
            System.arraycopy(strPath1.getBytes(), 0, ptrByteArray1.byValue, 0, strPath1.length());
            ptrByteArray1.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_LIBEAY_PATH, ptrByteArray1.getPointer());
            System.arraycopy(strPath2.getBytes(), 0, ptrByteArray2.byValue, 0, strPath2.length());
            ptrByteArray2.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SSLEAY_PATH, ptrByteArray2.getPointer());
            String strPathCom = System.getProperty("user.dir") + "/src/main/resources/lib/hk/";
            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            System.arraycopy(strPathCom.getBytes(), 0, struComPath.sPath, 0, strPathCom.length());
            struComPath.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SDK_PATH, struComPath.getPointer());
        }
        // SDK初始化，一个程序只需要调用一次
        boolean initSuc = hCNetSDK.NET_DVR_Init();
        // 异常消息回调
        if (fExceptionCallBack == null)
        {
            fExceptionCallBack = new FExceptionCallBack_Imp();
        }
        Pointer pUser = null;
        if (!hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, fExceptionCallBack, pUser)) {
            return ;
        }
        System.out.println("设置异常消息回调成功");
        logger.info("设置异常消息回调成功");
        // 启动SDK写日志
        hCNetSDK.NET_DVR_SetLogToFile(3, "./sdkLog", false);
    }

    public synchronized void addClient(String ip, short port, String user, String psw) {
        if (clients.containsKey(ip)) {
            System.out.println("该设备已登录");
            logger.info("该设备已登录, {}" , ip);
            return;
        }

        try {
            int userID = loginDevice(ip, port, user, psw);
            if (userID == -1) {
                logger.info("该设备登入失败, {}" , ip);
            }
            clients.put(ip, userID);

            try {
                startRealPlay(ip, 1);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("播放失败, IP：{}, 异常：{}" , ip, e.getMessage());
            }

            executor.submit(() -> {
                while (true) {
                    try {
                        startRealPlay(ip, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.error("播放失败, IP：{}, 异常：{}", ip, e.getMessage());
                        try {
                            // 等待一段时间后重试
                            TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); // 恢复中断状态
                            logger.error("重试线程被中断");
                            break; // 退出循环
                        }
                    }
                }
            });
        } catch (Exception e) {
            logger.error("该设备登入失败, IP：{}, 异常：{}" , ip, e.getMessage());
        }

    }

    public synchronized void removeClient(String ip) {
        if (clients.containsKey(ip)) {
            int userID = clients.get(ip);
            try {
                // 退出程序时调用，每一台设备分别注销
                if (hCNetSDK.NET_DVR_Logout(userID)) {
                    System.out.println("注销成功");
                }
                // SDK反初始化，释放资源，只需要退出时调用一次
                hCNetSDK.NET_DVR_Cleanup();
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("SDK反初始化，释放资源，只需要退出时调用一次 Error: {}", e.getMessage());
            }
        }
        clients.remove(ip);
    }


    /**
     * 登录设备，支持 V40 和 V30 版本，功能一致。
     *
     * @param ip      设备IP地址
     * @param port    SDK端口，默认为设备的8000端口
     * @param user    设备用户名
     * @param psw     设备密码
     * @return 登录成功返回用户ID，失败返回-1
     */
    public int loginDevice(String ip, short port, String user, String psw) {
        // 创建设备登录信息和设备信息对象
        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();

        // 设置设备IP地址
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length));
        loginInfo.sDeviceAddress = deviceAddress;

        // 设置用户名和密码
        byte[] userName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] password = psw.getBytes();
        System.arraycopy(user.getBytes(), 0, userName, 0, Math.min(user.length(), userName.length));
        System.arraycopy(password, 0, loginInfo.sPassword, 0, Math.min(password.length, loginInfo.sPassword.length));
        loginInfo.sUserName = userName;

        // 设置端口和登录模式
        loginInfo.wPort = port;
        loginInfo.bUseAsynLogin = false; // 同步登录
        loginInfo.byLoginMode = 0; // 使用SDK私有协议

        // 执行登录操作
        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (userID == -1) {
            System.err.println("登录失败，错误码为: " + hCNetSDK.NET_DVR_GetLastError());
        } else {
            System.out.println(ip + " 设备登录成功！");
            // 处理通道号逻辑
            int startDChan = deviceInfo.struDeviceV30.byStartDChan;
            System.out.println("预览起始通道号: " + startDChan);
        }
        return userID; // 返回登录结果
    }

    public void startRealPlay(String ip, int channel) {
        int userID = clients.get(ip);
        if (userID == -1) {
            System.out.println("请先注册， ip: " + ip);
            logger.error("请先注册, ip: {}" , ip);
            return;
        }
        try {
            lPlayHandle = HkVideoPlay.getRealStreamData(userID, channel);
            /**
             * 实时取流开启成功后，调用播放库抓图，延时几秒保证取流进入回调进行解码
             */
            Thread.sleep(3000);
            // 播放库抓图
            HkVideoPlay.getPicbyPlayCtrl();
        } catch (Exception e) {
            logger.error("启动实时预览失败, ip: {}, 异常：{}" , ip, e.getMessage());
        }

    }

    public void stopRealPlay() {
        try {
            if (lPlayHandle != -1) {
                HkVideoPlay.stopRealStreamData(lPlayHandle);

            }
            executor.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("停止实时预览失败, 异常：{}" , e.getMessage());
        }
    }


    @PreDestroy
    public void stop() {
        stopRealPlay();

        if (clients != null && !clients.isEmpty()) {
            for (String ip : clients.keySet()) {
                removeClient(ip);
            }
        }
    }
}
