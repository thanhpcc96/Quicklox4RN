package com.sts.RNQuickblox;

import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.gson.Gson;
import com.quickblox.auth.QBAuth;
import com.quickblox.auth.session.QBSession;
import com.quickblox.auth.session.QBSettings;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBIncomingMessagesManager;
import com.quickblox.chat.QBRestChatService;
import com.quickblox.chat.QBSystemMessagesManager;
import com.quickblox.chat.exception.QBChatException;
import com.quickblox.chat.listeners.QBChatDialogMessageListener;
import com.quickblox.chat.model.QBChatDialog;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBDialogType;
import com.quickblox.chat.request.QBMessageGetBuilder;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.request.QBPagedRequestBuilder;
import com.quickblox.core.request.QBRequestBuilder;
import com.quickblox.core.request.QBRequestGetBuilder;
import com.quickblox.core.result.HttpStatus;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBMediaStreamManager;
import com.quickblox.videochat.webrtc.QBRTCCameraVideoCapturer;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCSession;

import org.jivesoftware.smack.SmackException;
import org.webrtc.CameraVideoCapturer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Created by Dat Tran on 3/22/17.
 */

public class RNQuickbloxModule extends ReactContextBaseJavaModule {
    private static final String TAG = RNQuickbloxModule.class.getSimpleName();

    private static final String DID_RECEIVE_CALL_SESSION = "DID_RECEIVE_CALL_SESSION";
    private static final String USER_ACCEPT_CALL = "USER_ACCEPT_CALL";
    private static final String USER_REJECT_CALL = "USER_REJECT_CALL";
    private static final String USER_HUNG_UP = "USER_HUNG_UP";
    private static final String SESSION_DID_CLOSE = "SESSION_DID_CLOSE";
    private  static  final String  RECEIVE_IMCOMING_MESSAGE="RECEIVE_IMCOMING_MESSAGE";

    private static final String PUBLIC_GROUP ="PUBLIC_GROUP";
    private static final String PRIVATE_GROUP ="PRIVATE_GROUP";
    private ReactApplicationContext reactApplicationContext;
    private Gson gson;

    public RNQuickbloxModule(final ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactApplicationContext = reactContext;
        QuickbloxHandler.getInstance().setQuickbloxClient(this, reactContext);
        this.gson = new Gson();
    }

    private JavaScriptModule getJSModule() {
        return reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
    }

    @Override
    public String getName() {
        return "RNQuickblox";
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(DID_RECEIVE_CALL_SESSION, DID_RECEIVE_CALL_SESSION);
        constants.put(USER_ACCEPT_CALL, USER_ACCEPT_CALL);
        constants.put(USER_REJECT_CALL, USER_REJECT_CALL);
        constants.put(USER_HUNG_UP, USER_HUNG_UP);
        constants.put(SESSION_DID_CLOSE, SESSION_DID_CLOSE);
        return constants;
    }

    @ReactMethod
    public void setupQuickblox(String AppId, String authKey, String authSecret, String accountKey) {
        QBSettings.getInstance().init(reactApplicationContext, AppId, authKey, authSecret);
        QBSettings.getInstance().setAccountKey(accountKey);
        QBChatService.setConfigurationBuilder(configurationBuilder());
        QBChatService.setDebugEnabled(true);
        QBRTCConfig.setDebugEnabled(true);
    }

    @ReactMethod
    public void connectUser(String userId, String password, Callback callback) {
        this.login(userId, password, callback);
    }

    @ReactMethod
    public void signUp(final String userName, final String password, String realName, String email, final Callback callback) {
        final QBUser user = new QBUser();
        user.setLogin(userName);
        user.setPassword(password);
        user.setEmail(email);
        user.setFullName(realName);
        QBUsers.signUp(user).performAsync(new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser qbUser, Bundle bundle) {
                QuickbloxHandler.getInstance().setCurrentUser(qbUser);
                login(userName, password, callback);
            }

            @Override
            public void onError(QBResponseException e) {
                if (e.getHttpStatusCode() == HttpStatus.SC_UNPROCESSABLE_ENTITY) {
                    login(userName, password, callback);
                } else {
                    callback.invoke(e.getMessage());
                }
            }
        });
    }

    @ReactMethod
    public void getUsers(final Callback callback) {
        QBPagedRequestBuilder pagedRequestBuilder = new QBPagedRequestBuilder();
        pagedRequestBuilder.setPage(1);
        pagedRequestBuilder.setPerPage(50);

        QBUsers.getUsers(pagedRequestBuilder).performAsync(new QBEntityCallback<ArrayList<QBUser>>() {
            @Override
            public void onSuccess(ArrayList<QBUser> qbUsers, Bundle bundle) {
                Log.i(TAG, "Users: " + qbUsers.toString());
                callback.invoke(gson.toJson(qbUsers));
            }

            @Override
            public void onError(QBResponseException e) {

            }
        });
    }

    @ReactMethod
    public void callToUsers(ReadableArray userIDs, final Integer callRequestId, final String realName, final String avatar) {
        List<Integer> ids = new ArrayList<>();
//        ids.add(25581924);

        for (int i = 0; i < userIDs.size(); i++)
            ids.add(userIDs.getInt(i));

        QuickbloxHandler.getInstance().startCall(ids, callRequestId, realName, avatar);
    }

    /**
     * config Chat messges
     * @return ConfigurationBuilder instance
     */
    private QBChatService.ConfigurationBuilder configurationBuilder(){
        QBChatService.ConfigurationBuilder configurationBuilder= new QBChatService.ConfigurationBuilder();
        configurationBuilder.setSocketTimeout(60);
        configurationBuilder.setKeepAlive(true);
        configurationBuilder.setUseTls(true);
        configurationBuilder.setAutojoinEnabled(true);
        configurationBuilder.setAutoMarkDelivered(true);
        configurationBuilder.setReconnectionAllowed(true);
        configurationBuilder.setAllowListenNetwork(true);
        return  configurationBuilder;
    }

    private void login(String userId, String password, final Callback callback) {
        Log.d("Vao Login","Vao Login");
        final QBUser user = new QBUser(userId, password);
        QBAuth.createSession(user).performAsync(new QBEntityCallback<QBSession>() {
            @Override
            public void onSuccess(QBSession qbSession, Bundle bundle) {
                user.setId(qbSession.getUserId());
                QBChatService chatService = QBChatService.getInstance();
                chatService.login(user, new QBEntityCallback() {
                    @Override
                    public void onSuccess(Object o, Bundle bundle) {
                        QuickbloxHandler.getInstance().setCurrentUser(user);
                        QuickbloxHandler.getInstance().init();
                        callback.invoke(user.getId());

                    }

                    @Override
                    public void onError(QBResponseException e) {
                        Log.d("Login loi","Login loi");
                        callback.invoke(e.getMessage());
                    }
                });
            }

            @Override
            public void onError(QBResponseException e) {
                Log.d("QBResponseException e","Login loi");
                callback.invoke(gson.toJson(e));
            }
        });
    }

    @ReactMethod
    public void acceptCall() {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("key", "value");
        QuickbloxHandler.getInstance().getSession().acceptCall(userInfo);
    }

    @ReactMethod
    public void hangUp() {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("key", "value");
        QuickbloxHandler.getInstance().getSession().hangUp(userInfo);
        QuickbloxHandler.getInstance().setSession(null);

        QuickbloxHandler.getInstance().release();
    }

    @ReactMethod
    public void rejectCall() {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("key", "value");
        QuickbloxHandler.getInstance().getSession().rejectCall(userInfo);
        QuickbloxHandler.getInstance().setSession(null);
    }
    
    @ReactMethod
    public void switchCamera(final Callback callback) { // add by Nguyen Nam Tien
        QBRTCCameraVideoCapturer videoCapturer = (QBRTCCameraVideoCapturer) (QuickbloxHandler.getInstance().getSession().getMediaStreamManager().getVideoCapturer());
        videoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean b) {
                callback.invoke(null, b);
            }

            @Override
            public void onCameraSwitchError(String s) {
                callback.invoke(s);
            }
        });
    }

    @ReactMethod
    public void toggleAudio() { // add by Nguyen Nam Tien
        QBMediaStreamManager mediaStreamManager = QuickbloxHandler.getInstance().getSession().getMediaStreamManager();
        mediaStreamManager.setAudioEnabled(!mediaStreamManager.isAudioEnabled());
    }

     @ReactMethod
    public void toggleVideo() { // add by Nguyen Nam Tien
        QBMediaStreamManager mediaStreamManager = QuickbloxHandler.getInstance().getSession().getMediaStreamManager();
        mediaStreamManager.setVideoEnabled(!mediaStreamManager.isVideoEnabled());
    }

    @ReactMethod
    public void setAudioEnabled(boolean isEnabled) {
        QBMediaStreamManager mediaStreamManager = QuickbloxHandler.getInstance().getSession().getMediaStreamManager();
        mediaStreamManager.setAudioEnabled(isEnabled);
    }

    /**
     * Set mute/unmute video
     * @param isEnabled
     */
    @ReactMethod
    public void setVideoEnabled(boolean isEnabled) {
        QBMediaStreamManager mediaStreamManager = QuickbloxHandler.getInstance().getSession().getMediaStreamManager();
        mediaStreamManager.setVideoEnabled(isEnabled);
    }


    /**
     * Config chat
     */
    @ReactMethod
    public void getListDialogsOfCurrentUser(String type, final Callback callback){
        QBDialogType Type=null;
        switch(type) {
            case PUBLIC_GROUP:
                Type = QBDialogType.PUBLIC_GROUP;
                break;
            case PRIVATE_GROUP:
                Type = QBDialogType.PRIVATE;
                break;
            default:
                Type = null;
        }

        QuickbloxHandler.getInstance().getChatDialogs(Type).performAsync(new QBEntityCallback<ArrayList<QBChatDialog>>() {
            @Override
            public void onSuccess(ArrayList<QBChatDialog> qbChatDialogs, Bundle bundle) {
                callback.invoke(gson.toJson(qbChatDialogs));
            }

            @Override
            public void onError(QBResponseException e) {
                callback.invoke(gson.toJson(e));
            }
        });
    }

    @ReactMethod
    public void createMessageWithListIDFriend(String name, String type, ReadableArray userIDs, final Callback callback){
        List<Integer> ids = new ArrayList<>();
        QBDialogType Type= null;
        switch (type){
            case PUBLIC_GROUP:
                Type= QBDialogType.PUBLIC_GROUP;
                break;
            default: Type= null;
        }
        for (int i=0; i< userIDs.size();i++){
            ids.add(userIDs.getInt(i));
        }

        QuickbloxHandler.getInstance().createMessageWithSelectedUsers(name,Type,ids).performAsync(
            new QBEntityCallback<QBChatDialog>() {
            @Override
            public void onSuccess(QBChatDialog qbChatDialog, Bundle bundle) {
                callback.invoke(qbChatDialog);
            }

            @Override
            public void onError(QBResponseException e) {
                callback.invoke(e.getMessage());
            }
        });

    }

    @ReactMethod
    public void createPrivateDialog(final int friendID, final Callback callback){
        final QBSystemMessagesManager systemMessagesManager = QBChatService.getInstance().getSystemMessagesManager();
        QuickbloxHandler.getInstance().createPrivateDialog(friendID).performAsync(new QBEntityCallback<QBChatDialog>() {
            @Override
            public void onSuccess(QBChatDialog qbChatDialog, Bundle bundle) {
                QBChatMessage qbChatMessage= new QBChatMessage();
                qbChatMessage.setDialogId(qbChatDialog.getDialogId());
                qbChatMessage.setProperty("dialog_type",String.valueOf(qbChatDialog.getType().getCode()));
                qbChatMessage.setProperty("dialog_name", String.valueOf(qbChatDialog.getName()));
//                qbChatMessage.setProperty("notification_type",CREATING_DIALOG);
                qbChatMessage.setRecipientId(friendID);
                try {
                    systemMessagesManager.sendSystemMessage(qbChatMessage);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
                callback.invoke(gson.toJson(qbChatDialog));
            }

            @Override
            public void onError(QBResponseException e) {
                callback.invoke(gson.toJson(e));
            }
        });
    }

    @ReactMethod
    public void initDialogForChat(final String idChatDialog, Callback callback){
        QBChatDialog chatDialog= QuickbloxHandler.getInstance().getChatDialogByID(idChatDialog);
        if(chatDialog==null) return;
        chatDialog.initForChat(QBChatService.getInstance());
        Log.d("initDialogForChat"," init thanh cong");

    }


    @ReactMethod
    public void retrieveMessagesOfChatDialog(String idChatDialog, final Callback callback){
        QBMessageGetBuilder qbMessageGetBuilder=new QBMessageGetBuilder();
        qbMessageGetBuilder.setLimit(500);
        QBChatDialog temp= QuickbloxHandler.getInstance().getChatDialogByID(idChatDialog);
        if(temp==null){
            callback.invoke(gson.toJson(new Error("Can't find Chat dialog")));
            return;
        }
        QBRestChatService.getDialogMessages(temp,qbMessageGetBuilder).performAsync(
                new QBEntityCallback<ArrayList<QBChatMessage>>() {
                    @Override
                    public void onSuccess(ArrayList<QBChatMessage> qbChatMessages, Bundle bundle) {
                        callback.invoke(gson.toJson(qbChatMessages));
                    }

                    @Override
                    public void onError(QBResponseException e) {
                        callback.invoke(gson.toJson(e));
                    }
                }
        );
    }

    @ReactMethod
    public void sendMessage(String dialogID, int friendId, String text, final Callback callback){
        QBChatDialog chatDialog= QuickbloxHandler.getInstance().getChatDialogByID(dialogID);
        if(chatDialog==null){
            callback.invoke(gson.toJson(new Error("Can't find ChatDialog to sent message")));
            return;
        }
        QBChatMessage chatMessage= new QBChatMessage();
        chatMessage.setRecipientId(friendId);
        chatMessage.setDialogId(dialogID);
        chatMessage.setSaveToHistory(true);
        chatDialog.sendMessage(chatMessage, new QBEntityCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid, Bundle bundle) {
                Log.d("Send message thanh cong", "Send success0");
                callback.invoke(new String("Send success"));
            }

            @Override
            public void onError(QBResponseException e) {
                callback.invoke(gson.toJson(e));
            }
        });

    }





    public void receiveCallSession(QBRTCSession session, Integer userId) {
        WritableMap params = Arguments.createMap();
        params.putInt("userId", userId);
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(DID_RECEIVE_CALL_SESSION, params);
    }

    public void userAcceptCall(Integer userId) {
        WritableMap params = Arguments.createMap();
        params.putString("", "");
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(USER_ACCEPT_CALL, params);
    }

    public void userRejectCall(Integer userId) {
        WritableMap params = Arguments.createMap();
        params.putString("", "");
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(USER_REJECT_CALL, params);
    }

    public void userHungUp(Integer userId) {
        WritableMap params = Arguments.createMap();
        params.putString("", "");
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(USER_HUNG_UP, params);
    }

    public void sessionDidClose(QBRTCSession session) {
        WritableMap params = Arguments.createMap();
        params.putString("", "");
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(SESSION_DID_CLOSE, params);
    }
     public void receiveImcomingMessage(String s, QBChatMessage qbChatMessage, Integer integer){
        WritableMap params= Arguments.createMap();
        params.putString("id", qbChatMessage.getId());
        params.putString("dialogId", qbChatMessage.getDialogId());
        params.putInt("senderID", qbChatMessage.getSenderId());
        params.putInt("recipientId", qbChatMessage.getRecipientId());
        params.putString("text", qbChatMessage.getBody());
        params.putDouble("dateSent", qbChatMessage.getDateSent());
        params.putString("s", s);
        params.putInt("integer", integer);
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(RECEIVE_IMCOMING_MESSAGE, params);


    }
}
