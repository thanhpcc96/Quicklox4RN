package com.sts.RNQuickblox;

import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.quickblox.auth.session.QBSettings;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBRestChatService;
import com.quickblox.chat.QBSignaling;
import com.quickblox.chat.QBWebRTCSignaling;
import com.quickblox.chat.exception.QBChatException;
import com.quickblox.chat.listeners.QBChatDialogMessageListener;
import com.quickblox.chat.listeners.QBVideoChatSignalingManagerListener;
import com.quickblox.chat.model.QBChatDialog;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBDialogType;
import com.quickblox.chat.utils.DialogUtils;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.request.QBRequestGetBuilder;
import com.quickblox.core.server.Performer;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.BaseSession;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientVideoTracksCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionConnectionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionStateCallback;
import com.quickblox.videochat.webrtc.exception.QBRTCException;
import com.quickblox.videochat.webrtc.view.QBRTCVideoTrack;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Dat Tran on 3/22/17.
 */

public class QuickbloxHandler implements QBRTCClientVideoTracksCallbacks<QBRTCSession>, QBRTCSessionConnectionCallbacks {

    private static final String TAG = QuickbloxHandler.class.getSimpleName();

    private static QuickbloxHandler instance;
    private ReactApplicationContext reactApplicationContext;

    public Integer getCaller() {
        return caller;
    }

    private Integer caller;

    public QuickbloxLocalVideoViewManager getLocalViewManager() {
        return localViewManager;
    }

    public void setLocalViewManager(QuickbloxLocalVideoViewManager localViewManager) {
        this.localViewManager = localViewManager;
    }

    private QuickbloxLocalVideoViewManager localViewManager;

    public QuickbloxRemoteVideoViewManager getRemoteVideoViewManager() {
        return remoteVideoViewManager;
    }

    public void setRemoteVideoViewManager(QuickbloxRemoteVideoViewManager remoteVideoViewManager) {
        this.remoteVideoViewManager = remoteVideoViewManager;
    }

    private QuickbloxRemoteVideoViewManager remoteVideoViewManager;


    public RNQuickbloxModule getQuickbloxClient() {
        return quickbloxClient;
    }

    public void setQuickbloxClient(RNQuickbloxModule quickbloxClient, ReactApplicationContext rctCtx) {
        this.quickbloxClient = quickbloxClient;
        reactApplicationContext = rctCtx;
//        this.quickbloxClient.setupQuickblox(APP_ID, AUTH_KEY, AUTH_SECRET, ACCOUNT_KEY);
    }

    private RNQuickbloxModule quickbloxClient;


    public QBRTCSession getSession() {
        return session;
    }

    public void setSession(QBRTCSession session) {
        if (session != null) {
            this.session = session;
            this.session.addSessionCallbacksListener(this);
            this.session.addVideoTrackCallbacksListener(this);
        } else {
            this.session.removeVideoTrackCallbacksListener(this);
            this.session.removeSessionCallbacksListener(this);
            this.session = session;
        }
    }

    private QBRTCSession session;

    public QBUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(QBUser currentUser) {
        this.currentUser = currentUser;
    }

    private QBUser currentUser;

    public QuickbloxHandler() {
    }

    /**
     * create group chat from user or friend selected
     * @String name
     * @Enum type
     * @List usersID
     */
    public Performer<QBChatDialog> createMessageWithSelectedUsers(String name, QBDialogType type , List<Integer> usersID){
        if(name==null || name.length()==0){
            name="Group created" + new Date().getTime();
        }
        if(type==null){
            type= QBDialogType.GROUP;
        }
        return QBRestChatService.createChatDialog(DialogUtils.buildDialog(name, type, usersID));
    }

    /**
     * Create Private Dialog
     * @param
     * @param friendID
     * @return
     */
    public Performer<QBChatDialog> createPrivateDialog(int friendID){
        return QBRestChatService.createChatDialog(DialogUtils.buildPrivateDialog(friendID));
    }

    /**
     *  getChatDialogs of currents user loged in session
     * @param type Type của Dialog, nếu type=null=>getAll; (PUBLIC_GROUP,PRIVATE,null)
     * QBRequestGetBuilder tùy chỉnh request
     * @return List Dialog của current user(callback)
     */
    public Performer<ArrayList<QBChatDialog>> getChatDialogs(QBDialogType type){
        // fix cứng
        QBRequestGetBuilder requestBuilderCustom= new QBRequestGetBuilder();
        requestBuilderCustom.setLimit(100);
        return QBRestChatService.getChatDialogs(type, requestBuilderCustom);

    }

    public QBChatDialog getChatDialogByID( String dialogID){
        QBChatDialog chatDialog = null ;
        try {
            chatDialog = QBRestChatService.getChatDialogById(dialogID).perform();
        } catch (QBResponseException e) {
            e.printStackTrace();
        }
        return chatDialog;
    }

    public void init() {
        QBChatService.getInstance().getVideoChatWebRTCSignalingManager()
                .addSignalingManagerListener(new QBVideoChatSignalingManagerListener() {
                    @Override
                    public void signalingCreated(QBSignaling qbSignaling, boolean createdLocally) {
                        if (!createdLocally) {
                            QBRTCClient.getInstance(reactApplicationContext).addSignaling(qbSignaling);
                        }
                    }
                });
        // emit tin nhan moi nhan dk tu services ve native code len RN
        QBChatService.getInstance().getIncomingMessagesManager()
                .addDialogMessageListener(new QBChatDialogMessageListener() {
                    @Override
                    public void processMessage(String s, QBChatMessage qbChatMessage, Integer integer) {
                        quickbloxClient.receiveImcomingMessage(s,qbChatMessage,integer);
                    }

                    @Override
                    public void processError(String s, QBChatException e, QBChatMessage qbChatMessage, Integer integer) {

                    }
                });

        QBRTCClient.getInstance(reactApplicationContext).addSessionCallbacksListener(new QBRTCClientSessionCallbacks() {
            @Override
            public void onReceiveNewSession(QBRTCSession qbrtcSession) {

                if (session != null && qbrtcSession.getSessionID().equals(session.getSessionID())) {
                    session.rejectCall(new HashMap<String, String>() {{
                        put("key", "value");
                    }});
                    return;
                }

                setSession(qbrtcSession);
                //[self.localViewManager attachLocalCameraStream:self.session];
                quickbloxClient.receiveCallSession(session, Integer.valueOf(session.getUserInfo().get("userId")));
            }

            @Override
            public void onUserNoActions(QBRTCSession qbrtcSession, Integer integer) {

            }

            @Override
            public void onSessionStartClose(QBRTCSession qbrtcSession) {

            }

            @Override
            public void onUserNotAnswer(QBRTCSession qbrtcSession, Integer integer) {

            }

            @Override
            public void onCallRejectByUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {
                quickbloxClient.userRejectCall(integer);
            }

            @Override
            public void onCallAcceptByUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {
                quickbloxClient.userAcceptCall(integer);
            }

            @Override
            public void onReceiveHangUpFromUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {
                QuickbloxHandler.this.release();
                quickbloxClient.userHungUp(integer);
            }

            @Override
            public void onSessionClosed(QBRTCSession qbrtcSession) {
                quickbloxClient.sessionDidClose(qbrtcSession);
                session = null;
            }
        });

        QBRTCClient.getInstance(reactApplicationContext).prepareToProcessCalls();
    }

    public static QuickbloxHandler getInstance() {
        if (instance == null)
            instance = new QuickbloxHandler();

        return instance;
    }

    public void release() {
        if (QuickbloxHandler.this.localViewManager != null)
            QuickbloxHandler.this.localViewManager.release();
        if (QuickbloxHandler.this.remoteVideoViewManager != null)
            QuickbloxHandler.this.remoteVideoViewManager.release();
    }

    public void startCall(List<Integer> userIDs, Integer callRequestId, String realName, String avatar) {
        Log.d(TAG, "start call user: " + userIDs.toString() + " " + realName);

        QBRTCSession session = QBRTCClient.getInstance(reactApplicationContext).createNewSessionWithOpponents(userIDs, QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO);
        this.setSession(session);

        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("callRequestId", callRequestId.toString());
        userInfo.put("sessionId", session.getSessionID());
        userInfo.put("realName", realName);
        userInfo.put("avatar", avatar);
        userInfo.put("userId", this.currentUser.getId().toString());
        this.session.startCall(userInfo);
    }


    //<editor-fold desc="QBRTCSessionStateCallback">
    @Override
    public void onStateChanged(QBRTCSession session, BaseSession.QBRTCSessionState qbrtcSessionState) {

    }

    @Override
    public void onConnectedToUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onConnectionClosedForUser(QBRTCSession qbrtcSession, Integer integer) {

    }
    //</editor-fold>

    //<editor-fold desc="QBRTCClientVideoTracksCallbacks">
    @Override
    public void onLocalVideoTrackReceive(QBRTCSession qbrtcSession, QBRTCVideoTrack qbrtcVideoTrack) {
        Log.d(TAG, "onLocalVideoTrackReceive");
        if (localViewManager != null)
            localViewManager.renderVideoTrack(qbrtcVideoTrack);
    }

    @Override
    public void onRemoteVideoTrackReceive(QBRTCSession qbrtcSession, QBRTCVideoTrack qbrtcVideoTrack, Integer integer) {
        Log.d(TAG, "onRemoteVideoTrackReceive");
        if (remoteVideoViewManager != null)
            remoteVideoViewManager.renderVideoTrack(qbrtcVideoTrack);
        caller = integer;
    }
    //</editor-fold>

    //<editor-fold desc="QBRTCSessionConnectionCallbacks">
    @Override
    public void onStartConnectToUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onDisconnectedTimeoutFromUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onConnectionFailedWithUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    public void onError(QBRTCSession qbrtcSession, QBRTCException e) {

    }
    //</editor-fold>
}
