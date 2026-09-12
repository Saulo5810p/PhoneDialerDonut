/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ============================================================================
 * ADAPTADO (Rota A / DonutCallManager) — mudanças em relação ao 1.6 original:
 *
 * 1. Não existe mais com.android.internal.telephony.{Call,CallerInfo,
 *    CallerInfoAsyncQuery,Connection,MmiCode,Phone} — todo o estado de
 *    chamada agora vem do DonutCallManager (android.telecom.Call),
 *    observado via DonutCallManager.Listener (em vez de
 *    Phone.registerForPhoneStateChanged/registerForDisconnect/
 *    registerForMmiInitiate/registerForMmiComplete/registerForCallWaiting/
 *    setOnPostDialCharacter/registerForSuppServiceFailed).
 * 2. MMI/USSD (onMMIInitiate/onMMICancel/handlePostOnDialChars/
 *    showWaitPromptDialog/showWildPromptDialog/addVoiceMailNumberPanel)
 *    foi removido — dependia do motor RIL interno (MmiCode), sem
 *    equivalente público. Mesmo corte já documentado em PhoneUtils.
 * 3. onSuppServiceFailed (Phone.SuppService) removido — evento RIL
 *    interno sem equivalente em android.telecom.
 * 4. Toda a distinção GSM/CDMA (mPhone.getPhoneName(), CDMA call
 *    waiting/3-way/CdmaPhoneCallState) foi removida — mesmo corte já
 *    aplicado em CallCard/InCallMenu/PhoneUtils/DTMFTwelveKeyDialer.
 * 5. Bluetooth handsfree: o antigo BluetoothHandsfree/BluetoothHeadsetService
 *    (stack de AT commands rodada pelo próprio app, com HeadsetBase/ScoSocket/
 *    AtParser -- tudo removido do SDK público) foi excluído do build.
 *    O roteamento de áudio Bluetooth agora é feito via CallAudioState (API
 *    pública do Telecom), consultado por PhoneApp.showBluetoothIndication()/
 *    PhoneUtils.isBluetoothAvailable()/isBluetoothAudioOn() -- ver
 *    telecom/DonutCallManager.java.
 * 6. "Manage conference": em vez de List<Connection> (motor interno),
 *    usamos Call.getChildren() (lista de android.telecom.Call — API
 *    pública para conferências). PhoneUtils.separateCall/hangup passam a
 *    operar sobre Call em vez de Connection.
 * 7. O grid de controle de chamada (InCallMenu/InCallMenuView), que no
 *    Donut original só aparecia via tecla física MENU
 *    (onCreatePanelView/onPanelClosed), agora é criado em
 *    initInCallScreen() e fica SEMPRE visível, ancorado em
 *    R.id.inCallMenuContainer (ver incall_screen.xml) — sem isso a tela
 *    de chamada fica sem nenhum controle visível em aparelhos modernos,
 *    que não têm mais essa tecla. onCreatePanelView/onPanelClosed foram
 *    mantidos como fallback inofensivo para teclados físicos residuais,
 *    mas o grid não depende mais deles.
 * 8. Touch lock overlay, "manage conference", DTMF dialpad, onscreen
 *    answer UI: lógica de UI preservada, só trocando a fonte de estado
 *    (Phone/Call internos -> DonutCallManager/android.telecom.Call).
 * ============================================================================
 */

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import com.android.phone.compat.TelephonyIntentsCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Chronometer;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SlidingDrawer;
import android.widget.TextView;

import com.android.phone.compat.CallerInfo;
import com.android.phone.compat.CallerInfoAsyncQuery;
import com.android.phone.telecom.DonutCallManager;

import java.util.List;

/**
 * Phone app "in call" screen. Versão adaptada para operar sobre
 * android.telecom.Call (via DonutCallManager) em vez do motor de
 * telefonia interno do AOSP — ver nota de adaptação no topo do arquivo.
 */
public class InCallScreen extends Activity
        implements View.OnClickListener, View.OnTouchListener,
                CallerInfoAsyncQuery.OnQueryCompleteListener,
                DonutCallManager.Listener {
    private static final String LOG_TAG = "InCallScreen";

    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    /**
     * Intent extra used to specify whether the DTMF dialpad should be
     * initially visible when bringing up the InCallScreen.
     */
    static final String SHOW_DIALPAD_EXTRA = "com.android.phone.ShowDialpad";

    // Amount of time (in msec) that we display the "Call ended" state.
    private static final int CALL_ENDED_SHORT_DELAY =  200;  // msec
    private static final int CALL_ENDED_LONG_DELAY = 2000;  // msec

    // The "touch lock" overlay timeout.
    private static final int TOUCH_LOCK_DELAY_DEFAULT =  6000;  // msec

    // Máximo de participantes exibidos no painel "Manage conference".
    private static final int MAX_CALLERS_IN_CONFERENCE = 5;

    // Código de requestPermissions() para POST_NOTIFICATIONS (API 33+).
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1;

    // Message codes; see mHandler below.
    private static final int EVENT_HEADSET_PLUG_STATE_CHANGED = 103;
    private static final int DELAYED_CLEANUP_AFTER_DISCONNECT = 108;
    private static final int ALLOW_SCREEN_ON = 112;
    private static final int TOUCH_LOCK_TIMER = 113;

    // High-level "modes" of the in-call UI.
    private enum InCallScreenMode {
        /** Normal in-call UI elements visible. */
        NORMAL,
        /** "Manage conference" UI is visible, totally replacing the normal in-call UI. */
        MANAGE_CONFERENCE,
        /** Non-interactive UI state; call card shows the call that just ended. */
        CALL_ENDED
    }
    private InCallScreenMode mInCallScreenMode;

    // Possible error conditions on startup.
    private enum InCallInitStatus {
        SUCCESS,
        PHONE_NOT_IN_USE,
        NO_PHONE_NUMBER_SUPPLIED,
        CALL_FAILED
    }
    private InCallInitStatus mInCallInitialStatus;  // see onResume()

    // Main in-call UI ViewGroups
    private ViewGroup mMainFrame;
    private ViewGroup mInCallPanel;

    // Menu button hint below the "main frame" (landscape only)
    private TextView mMenuButtonHint;

    // Main in-call UI elements:
    private CallCard mCallCard;
    private InCallMenu mInCallMenu;  // sempre criado e visível (ver initInCallScreen)
    private ViewGroup mInCallMenuContainer;

    /**
     * DTMF Dialer objects.
     */
    private DTMFTwelveKeyDialer mDialer;
    private SlidingDrawer mDialerDrawer;
    private EditText mDTMFDisplay;

    // "Manage conference" UI elements
    private ViewGroup mManageConferencePanel;
    private Button mButtonManageConferenceDone;
    private ViewGroup[] mConferenceCallList;
    private int mNumCallersInConference;
    private Chronometer mConferenceTime;

    // "Touch lock" overlay graphic
    private View mTouchLockOverlay;
    private View mTouchLockIcon;
    private Animation mTouchLockFadeIn;
    private long mTouchLockLastTouchTime;

    // Onscreen "answer" UI, for devices with no hardware CALL button.
    private View mOnscreenAnswerUiContainer;
    private View mOnscreenAnswerButton;
    private long mOnscreenAnswerButtonLastTouchTime;

    // Various dialogs we bring up (see dismissAllDialogs())
    private AlertDialog mGenericErrorDialog;
    private AlertDialog mSuppServiceFailureDialog;

    private boolean mIsDestroyed = false;
    private boolean mIsForegroundActivity = false;

    // Se true, mostra o Call Log ao sair da UI de chamada por a última
    // chamada desconectada ter sido iniciada pelo usuário.
    private boolean mShowCallLogAfterDisconnect;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mIsDestroyed) {
                if (DBG) log("Handler: ignoring message " + msg + "; we're destroyed!");
                return;
            }

            PhoneApp app = PhoneApp.getInstance();
            switch (msg.what) {
                case EVENT_HEADSET_PLUG_STATE_CHANGED:
                    if (msg.arg1 != 1) {
                        PhoneUtils.restoreSpeakerMode(getApplicationContext());
                    }
                    updateScreen();
                    break;

                case DELAYED_CLEANUP_AFTER_DISCONNECT:
                    delayedCleanupAfterDisconnect();
                    break;

                case ALLOW_SCREEN_ON:
                    if (VDBG) log("ALLOW_SCREEN_ON message...");
                    app.preventScreenOn(false);
                    break;

                case TOUCH_LOCK_TIMER:
                    if (VDBG) log("TOUCH_LOCK_TIMER...");
                    touchLockTimerExpired();
                    break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    int state = intent.getIntExtra("state", 0);
                    mHandler.sendMessage(mHandler.obtainMessage(
                            EVENT_HEADSET_PLUG_STATE_CHANGED, state, 0));
                }
            }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        if (DBG) log("onCreate()...  this = " + this);

        Profiler.callScreenOnCreate();

        super.onCreate(icicle);

        final PhoneApp app = PhoneApp.getInstance();
        app.setInCallScreenInstance(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Inflate everything in incall_screen.xml and add it to the screen.
        setContentView(R.layout.incall_screen);
        mDialerDrawer = (SlidingDrawer) findViewById(R.id.dialer_container);

        initInCallScreen();

        // Create the dtmf dialer.
        mDialer = new DTMFTwelveKeyDialer(this);

        DonutCallManager.getInstance().addListener(this);

        // POST_NOTIFICATIONS (obrigatória em runtime a partir do Android 13/
        // API 33) -- sem isso a notificação de chamada em andamento/chamada
        // perdida do NotificationMgr simplesmente não aparece, mesmo já
        // declarada no manifest. InCallScreen é o ponto de entrada mais
        // universal do app (toda chamada passa por aqui, diferente das
        // outras Activities que só abrem por navegação manual), então é
        // onde faz sentido pedir. Falha silenciosamente se negada -- o app
        // continua funcionando, só sem notificação visível.
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                    REQUEST_CODE_POST_NOTIFICATIONS);
        }

        if (icicle == null) {
            if (DBG) log("onCreate(): this is our very first launch, checking intent...");
            mInCallInitialStatus = internalResolveIntent(getIntent());
            if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
                Log.w(LOG_TAG, "onCreate: status " + mInCallInitialStatus
                      + " from internalResolveIntent()");
            }
        } else {
            mInCallInitialStatus = InCallInitStatus.SUCCESS;
        }

        if (ConfigurationHelper.isLandscape()) {
            mDialer.startDialerSession();
            if (VDBG) log("Dialer initialized (in landscape mode).");
        }

        Profiler.callScreenCreated();
    }

    @Override
    protected void onResume() {
        if (DBG) log("onResume()...");
        super.onResume();

        mIsForegroundActivity = true;

        final PhoneApp app = PhoneApp.getInstance();

        app.disableKeyguard();
        app.setIgnoreTouchUserActivity(true);

        // Listen for broadcast intents that might affect the onscreen UI.
        registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));

        if (DBG) log("- onResume: initial status = " + mInCallInitialStatus);
        if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
            if (DBG) log("- onResume: failure during startup: " + mInCallInitialStatus);
            handleStartupError(mInCallInitialStatus);
            mInCallInitialStatus = InCallInitStatus.SUCCESS;
        }

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        takeKeyEvents(true);

        // Always start off in NORMAL mode.
        setInCallScreenMode(InCallScreenMode.NORMAL);

        InCallInitStatus status = syncWithPhoneState();
        if (status != InCallInitStatus.SUCCESS) {
            if (DBG) log("- syncWithPhoneState failed! status = " + status);
            // Não damos finish() imediatamente; um diálogo de erro pode
            // estar sendo mostrado, e ele é quem chama finish() ao fechar.
        }

        // Se está tocando, garante que a tela acende (equivalente ao
        // antigo preventScreenOn(true) chamado antes do launch).
        if (hasRingingCall()) {
            mHandler.removeMessages(ALLOW_SCREEN_ON);
            mHandler.sendEmptyMessage(ALLOW_SCREEN_ON);
        } else {
            app.preventScreenOn(false);
        }
        app.updateWakeState();

        // A "touch lock" overlay NUNCA fica visível ao retomar.
        enableTouchLock(false);
        if (mDialer.isOpened()) resetTouchLockTimer();

        if (app.getRestoreMuteOnInCallResume()) {
            PhoneUtils.setMute(false);
            app.setRestoreMuteOnInCallResume(false);
        }

        Profiler.profileViewCreate(getWindow(), InCallScreen.class.getName());
        if (VDBG) log("onResume() done.");
    }

    @Override
    protected void onPause() {
        if (DBG) log("onPause()...");
        super.onPause();

        mIsForegroundActivity = false;

        final PhoneApp app = PhoneApp.getInstance();

        if (mConferenceTime != null) {
            mConferenceTime.stop();
        }

        // Catch-all: garante que nenhum tom DTMF continua tocando quando
        // a UI sai de primeiro plano.
        mDialer.onDialerKeyUp(null);

        if (mHandler.hasMessages(DELAYED_CLEANUP_AFTER_DISCONNECT)) {
            if (DBG) log("DELAYED_CLEANUP_AFTER_DISCONNECT detected, moving UI to background.");
            finish();
        }

        // Dismiss any dialogs we may have brought up.
        dismissAllDialogs();

        unregisterReceiver(mReceiver);

        mHandler.postDelayed(new Runnable() {
                public void run() {
                    app.setIgnoreTouchUserActivity(false);
                }
            }, 500);

        app.reenableKeyguard();
        app.updateWakeState();
    }

    @Override
    protected void onStop() {
        if (VDBG) log("onStop()...");
        super.onStop();

        stopTimer();

        if (VDBG) log("onStop: hasCalls = " + hasAnyCalls());

        if (!hasAnyCalls()) {
            // Não queremos que a tela de chamada permaneça no histórico
            // se não há mais nenhuma chamada ativa ou tocando.
            if (DBG) log("- onStop: calling finish() to clear activity history...");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (DBG) log("onDestroy()...");
        super.onDestroy();

        mIsDestroyed = true;

        final PhoneApp app = PhoneApp.getInstance();
        app.setInCallScreenInstance(null);

        if (mInCallMenu != null) {
            mInCallMenu.clearInCallScreenReference();
        }
        if (mCallCard != null) {
            mCallCard.setInCallScreenInstance(null);
        }

        if (ConfigurationHelper.isLandscape()) {
            mDialer.stopDialerSession();
        } else {
            mDialer.closeDialer(false);
        }
        mDialer.clearInCallScreenReference();
        mDialer = null;

        DonutCallManager.getInstance().removeListener(this);
    }

    /**
     * Dismisses the in-call screen.
     *
     * Nunca damos finish() de verdade — só movemos a Activity para trás
     * na pilha, pra reaproveitar a mesma instância na próxima chamada
     * (comportamento idêntico ao Donut original).
     */
    @Override
    public void finish() {
        if (DBG) log("finish()...");
        moveTaskToBack(true);
    }

    /* package */ boolean isForegroundActivity() {
        return mIsForegroundActivity;
    }

    /**
     * Chamado pelo PhoneApp.updateBluetoothIndication() quando o estado
     * do Bluetooth handsfree muda enquanto esta tela está em primeiro
     * plano. Delega ao CallCard, que é quem desenha o indicador.
     */
    /* package */ void updateBluetoothIndication() {
        if (mCallCard != null) {
            mCallCard.updateBluetoothIndication();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DBG) log("onNewIntent: intent=" + intent);

        setIntent(intent);

        mInCallInitialStatus = internalResolveIntent(intent);
        if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
            Log.w(LOG_TAG, "onNewIntent: status " + mInCallInitialStatus
                  + " from internalResolveIntent()");
        }
    }

    private InCallInitStatus internalResolveIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return InCallInitStatus.SUCCESS;
        }

        String action = intent.getAction();
        if (DBG) log("internalResolveIntent: action=" + action);

        final PhoneApp app = PhoneApp.getInstance();
        if (action.equals(Intent.ACTION_ANSWER)) {
            internalAnswerCall();
            app.setRestoreMuteOnInCallResume(false);
            return InCallInitStatus.SUCCESS;
        } else if (action.equals(Intent.ACTION_CALL)
                || action.equals(TelephonyIntentsCompat.ACTION_CALL_EMERGENCY)) {
            // Discar agora é responsabilidade do sistema/TwelveKeyDialer
            // (ACTION_CALL normal); a InCallScreen só precisa reagir ao
            // evento de chamada nova via DonutCallManager.Listener.
            app.setRestoreMuteOnInCallResume(false);
            return InCallInitStatus.SUCCESS;
        } else if (action.equals(Intent.ACTION_MAIN)) {
            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
                boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
                if (VDBG) log("- internalResolveIntent: SHOW_DIALPAD_EXTRA value = " + showDialpad);
                if (showDialpad) {
                    mDialer.openDialer(false);
                } else {
                    mDialer.closeDialer(false);
                }
            }
            return InCallInitStatus.SUCCESS;
        } else {
            Log.w(LOG_TAG, "internalResolveIntent: unexpected intent action: " + action);
            return InCallInitStatus.SUCCESS;
        }
    }

    private void stopTimer() {
        if (mCallCard != null) mCallCard.stopTimer();
    }

    private void initInCallScreen() {
        if (VDBG) log("initInCallScreen()...");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

        mMainFrame = (ViewGroup) findViewById(R.id.mainFrame);
        mInCallPanel = (ViewGroup) findViewById(R.id.inCallPanel);

        ConfigurationHelper.initConfiguration(getResources().getConfiguration());

        // Cria o CallCard e adiciona à hierarquia de Views.
        View callCardLayout = getLayoutInflater().inflate(
                R.layout.call_card_popup,
                mInCallPanel);
        mCallCard = (CallCard) callCardLayout.findViewById(R.id.callCard);
        if (VDBG) log("  - mCallCard = " + mCallCard);
        mCallCard.setInCallScreenInstance(this);

        // Menu Button hint (landscape)
        mMenuButtonHint = (TextView) findViewById(R.id.menuButtonHint);

        // ADAPTADO (Rota A): sem tecla física MENU nos aparelhos modernos,
        // o grid de controle de chamada (InCallMenu) precisa existir e
        // ficar visível DESDE O PRIMEIRO FRAME, não mais só quando o
        // usuário aperta MENU. Criamos e inflamos aqui, dentro do
        // container fixo R.id.inCallMenuContainer (ver incall_screen.xml).
        mInCallMenu = new InCallMenu(this);
        mInCallMenu.initMenu();
        mInCallMenuContainer = (ViewGroup) findViewById(R.id.inCallMenuContainer);
        if (mInCallMenuContainer != null && mInCallMenu.getView() != null) {
            mInCallMenuContainer.addView(mInCallMenu.getView());
        }

        // Onscreen "answer" UI (dispositivos sem tecla física CALL).
        initOnscreenAnswerUi();

        ConfigurationHelper.applyConfigurationToLayout(this);
    }

    // ------------------------------------------------------------------
    // Helpers de estado de chamada (substituem mForegroundCall/
    // mBackgroundCall/mRingingCall/mPhone.getState() do original).
    // ------------------------------------------------------------------

    private Call ringingCall() {
        for (Call c : DonutCallManager.getInstance().getCalls()) {
            if (c.getState() == Call.STATE_RINGING) return c;
        }
        return null;
    }

    private Call activeCall() {
        for (Call c : DonutCallManager.getInstance().getCalls()) {
            int s = c.getState();
            if (s == Call.STATE_ACTIVE || s == Call.STATE_DIALING || s == Call.STATE_CONNECTING) {
                return c;
            }
        }
        return null;
    }

    private Call holdingCall() {
        for (Call c : DonutCallManager.getInstance().getCalls()) {
            if (c.getState() == Call.STATE_HOLDING) return c;
        }
        return null;
    }

    private boolean hasRingingCall() {
        return ringingCall() != null;
    }

    private boolean hasAnyCalls() {
        return !DonutCallManager.getInstance().getCalls().isEmpty();
    }

    /**
     * @return true se o telefone está "em uso" (ao menos uma chamada
     * ativa, tocando, ou discando). Equivalente ao antigo phoneIsInUse().
     */
    private boolean phoneIsInUse() {
        return hasAnyCalls();
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        if (VDBG) log("handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");
        if (okToDialDTMFTones()) {
            return mDialer.onDialerKeyDown(event);
        }
        return false;
    }

    /**
     * Handles a DOWN keypress on the BACK key.
     */
    private boolean handleBackKey() {
        if (VDBG) log("handleBackKey()...");

        // Enquanto uma chamada está tocando, BACK se comporta como
        // ENDCALL: para o toque e recusa a chamada.
        if (hasRingingCall()) {
            if (DBG) log("BACK key while ringing: reject the call");
            internalHangupRingingCall();
            return false;
        }

        if (mDialer.isOpened()) {
            enableTouchLock(false);
            mDialer.closeDialer(true);
            return true;
        }

        if (mInCallScreenMode == InCallScreenMode.MANAGE_CONFERENCE) {
            setInCallScreenMode(InCallScreenMode.NORMAL);
            return true;
        }

        return false;
    }

    /**
     * Handles the green CALL key while in-call.
     */
    private boolean handleCallKey() {
        final boolean hasRingingCall = hasRingingCall();
        final boolean hasActiveCall = activeCall() != null;
        final boolean hasHoldingCall = holdingCall() != null;

        if (hasRingingCall) {
            if (hasActiveCall && hasHoldingCall) {
                if (DBG) log("handleCallKey: ringing (both lines in use) ==> answer!");
                internalAnswerCallBothLinesInUse();
            } else {
                if (DBG) log("handleCallKey: ringing ==> answer!");
                internalAnswerCall();
            }
        } else if (hasActiveCall && hasHoldingCall) {
            if (DBG) log("handleCallKey: both lines in use ==> swap calls.");
            internalSwapCalls();
        } else if (hasHoldingCall) {
            if (DBG) log("handleCallKey: call on hold ==> unhold.");
            PhoneUtils.switchHoldingAndActive();
        } else {
            if (VDBG) log("handleCallKey: call in foregound ==> ignoring.");
        }

        // Sempre consumimos a tecla CALL.
        return true;
    }

    boolean isKeyEventAcceptableDTMF(KeyEvent event) {
        return (mDialer != null && mDialer.isKeyEventAcceptable(event));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (VDBG) log("onWindowFocusChanged(" + hasFocus + ")...");
        if (!hasFocus && mDialer != null) {
            if (VDBG) log("- onWindowFocusChanged: faking onDialerKeyUp()...");
            mDialer.onDialerKeyUp(null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mDialer.isOpened() && isTouchLocked()) {
                    if (DBG) log("- ignoring DPAD event while touch-locked...");
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((mDialer != null) && (mDialer.onDialerKeyUp(event))) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                boolean handled = handleCallKey();
                if (!handled) {
                    Log.w(LOG_TAG, "InCallScreen should always handle KEYCODE_CALL in onKeyDown");
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (handleBackKey()) {
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_CAMERA:
                // Desabilita a CAMERA durante a chamada (fácil de apertar sem querer).
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (hasRingingCall()) {
                    PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
                    if (DBG) log("VOLUME key: silence ringer");
                    PhoneApp.getInstance().notifier.silenceRinger();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_MENU:
                if (mDialer.isOpened() && isTouchLocked()) {
                    if (VDBG) log("- allowing MENU to dismiss touch lock overlay...");
                    enableTouchLock(false);
                    resetTouchLockTimer();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_MUTE:
                PhoneUtils.setMute(!PhoneUtils.getMute());
                return true;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Chamado quando o Handler recebe algo relevante — hoje só usado
     * como catch-all de log; a maior parte do trabalho acontece
     * diretamente em onCallAdded/onCallStateChanged/onCallRemoved.
     */
    private void onPhoneStateChangedInternal() {
        if (VDBG) log("onPhoneStateChangedInternal()...");
        if (!mIsForegroundActivity) {
            if (VDBG) log("- onPhoneStateChangedInternal: not the foreground Activity!");
        }
        updateScreen();

        final PhoneApp app = PhoneApp.getInstance();
        app.updateWakeState();

        // Se ficamos totalmente ociosos, aciona a limpeza (equivalente ao
        // antigo onDisconnect() do Donut).
        if (!hasAnyCalls() && mInCallScreenMode != InCallScreenMode.CALL_ENDED) {
            onAllCallsDisconnected();
        }
    }

    /**
     * Chamado quando a última chamada termina e não sobra nada. Decide o
     * delay de "Call ended" e agenda a limpeza — equivalente ao trecho
     * final do antigo onDisconnect(AsyncResult).
     */
    private void onAllCallsDisconnected() {
        if (DBG) log("onAllCallsDisconnected()...");

        setInCallScreenMode(InCallScreenMode.CALL_ENDED);

        final int delay = mShowCallLogAfterDisconnect
                ? CALL_ENDED_SHORT_DELAY : CALL_ENDED_LONG_DELAY;
        mHandler.removeMessages(DELAYED_CLEANUP_AFTER_DISCONNECT);
        mHandler.sendEmptyMessageDelayed(DELAYED_CLEANUP_AFTER_DISCONNECT, delay);
    }

    private void delayedCleanupAfterDisconnect() {
        if (VDBG) log("delayedCleanupAfterDisconnect()...");

        setInCallScreenMode(InCallScreenMode.NORMAL);

        if (!mIsForegroundActivity && !hasAnyCalls()) {
            if (DBG) log("- delayedCleanupAfterDisconnect: not foreground, finishing...");
        }

        if (!hasAnyCalls()) {
            if (mShowCallLogAfterDisconnect) {
                Intent intent = PhoneApp.createCallLogIntent();
                startActivity(intent);
                mShowCallLogAfterDisconnect = false;
            }
            finish();
        }
    }

    private void updateScreen() {
        if (VDBG) log("updateScreen()...");

        if (!mIsForegroundActivity) {
            if (VDBG) log("- updateScreen: not the foreground Activity! Bailing out...");
            return;
        }

        // Atualiza o estado do grid de controle sempre (agora está sempre
        // visível quando há uma chamada em que ele faz sentido).
        updateInCallMenuVisibility();

        if (mInCallScreenMode == InCallScreenMode.MANAGE_CONFERENCE) {
            if (VDBG) log("- updateScreen: manage conference mode (NOT updating in-call UI)...");
            updateManageConferencePanelIfNecessary();
            return;
        } else if (mInCallScreenMode == InCallScreenMode.CALL_ENDED) {
            if (VDBG) log("- updateScreen: call ended state (NOT updating in-call UI)...");
            return;
        }

        if (VDBG) log("- updateScreen: updating the in-call UI...");
        mCallCard.updateState();
        updateDialpadVisibility();
        updateOnscreenAnswerUi();
        updateMenuButtonHint();
    }

    /**
     * (Re)sincroniza a UI com o estado atual das chamadas.
     */
    private InCallInitStatus syncWithPhoneState() {
        if (DBG) log("syncWithPhoneState()...");

        if (!hasAnyCalls()) {
            if (DBG) log("syncWithPhoneState: phone is idle; we shouldn't be in here.");
            return InCallInitStatus.PHONE_NOT_IN_USE;
        }

        updateScreen();
        return InCallInitStatus.SUCCESS;
    }

    private void handleMissingVoiceMailNumber() {
        // Sem acesso a escrita de voicemail do sistema nesta arquitetura
        // (ver CallFeaturesSetting) — mantido como no-op seguro.
        if (DBG) log("handleMissingVoiceMailNumber() - no-op nesta arquitetura");
    }

    // ------------------------------------------------------------------
    // DonutCallManager.Listener
    // ------------------------------------------------------------------

    @Override
    public void onCallAdded(Call call) {
        if (VDBG) log("onCallAdded: " + call);
        onPhoneStateChangedInternal();
    }

    @Override
    public void onCallStateChanged(Call call) {
        if (VDBG) log("onCallStateChanged: " + call);
        onPhoneStateChangedInternal();
    }

    @Override
    public void onCallRemoved(Call call) {
        if (VDBG) log("onCallRemoved: " + call);
        // A chamada que acabou de ser discada pelo usuário (vs. recebida)
        // decide se mostramos o Call Log ao sair.
        mShowCallLogAfterDisconnect = (call.getDetails() != null)
                && (call.getDetails().getCallDirection() == Call.Details.DIRECTION_OUTGOING);
        onPhoneStateChangedInternal();
    }

    @Override
    public void onAudioStateChanged(CallAudioState state) {
        if (VDBG) log("onAudioStateChanged: " + state);
        updateScreen();
    }

    /**
     * Handle a click on any view within the in-call UI (mostly the
     * InCallMenu grid, but also the "manage conference done" button).
     */
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (VDBG) log("onClick(View " + view + ", id " + id + ")...");

        Context context = getApplicationContext();

        if (id == R.id.menuAnswerAndHold) {
            if (VDBG) log("onClick: AnswerAndHold...");
            internalAnswerCall();

        } else if (id == R.id.menuAnswerAndEnd) {
            if (VDBG) log("onClick: AnswerAndEnd...");
            internalAnswerAndEnd();

        } else if (id == R.id.menuAnswer) {
            if (DBG) log("onClick: Answer...");
            internalAnswerCall();

        } else if (id == R.id.menuIgnore) {
            if (DBG) log("onClick: Ignore...");
            internalHangupRingingCall();

        } else if (id == R.id.menuSwapCalls) {
            if (VDBG) log("onClick: SwapCalls...");
            internalSwapCalls();

        } else if (id == R.id.menuMergeCalls) {
            if (VDBG) log("onClick: MergeCalls...");
            PhoneUtils.mergeCalls();

        } else if (id == R.id.menuManageConference) {
            if (VDBG) log("onClick: ManageConference...");
            setInCallScreenMode(InCallScreenMode.MANAGE_CONFERENCE);

        } else if (id == R.id.menuShowDialpad) {
            if (VDBG) log("onClick: Show/hide dialpad...");
            if (mDialer.isOpened()) {
                mDialer.closeDialer(true);
            } else {
                mDialer.openDialer(true);
            }

        } else if (id == R.id.manage_done) {
            if (VDBG) log("onClick: mButtonManageConferenceDone...");
            setInCallScreenMode(InCallScreenMode.NORMAL);

        } else if (id == R.id.menuSpeaker) {
            if (VDBG) log("onClick: Speaker...");
            boolean newSpeakerState = !PhoneUtils.isSpeakerOn(context);
            PhoneUtils.turnOnSpeaker(context, newSpeakerState, true);

            if (newSpeakerState) {
                enableTouchLock(false);
            } else if (mDialer.isOpened() && !isTouchLocked()) {
                resetTouchLockTimer();
            }

        } else if (id == R.id.menuMute) {
            if (VDBG) log("onClick: Mute...");
            PhoneUtils.setMute(!PhoneUtils.getMute());

        } else if (id == R.id.menuHold) {
            if (VDBG) log("onClick: Hold...");
            onHoldClick();

        } else if (id == R.id.menuAddCall) {
            if (VDBG) log("onClick: AddCall...");
            startActivity(new Intent(Intent.ACTION_DIAL));

        } else if (id == R.id.menuEndCall) {
            if (VDBG) log("onClick: EndCall...");
            PhoneUtils.hangupActiveCall();

        } else if (id == R.id.menuBluetooth) {
            // Alterna o roteamento de áudio pro Bluetooth via sistema
            // (CallAudioState, API pública) -- ver PhoneUtils/
            // DonutCallManager. O item só fica clicável quando o
            // InCallMenu já detectou uma rota Bluetooth disponível.
            if (VDBG) log("onClick: Bluetooth...");
            PhoneUtils.setBluetoothOn(!PhoneUtils.isBluetoothAudioOn());

        } else {
            Log.w(LOG_TAG,
                  "Got click from unexpected View ID " + id + " (View = " + view + ")");
        }

        // Qualquer clique no grid conta como atividade explícita do usuário.
        PhoneApp.getInstance().pokeUserActivity();

        // Reflete o novo estado imediatamente (ex.: LED de mudo/viva-voz).
        updateInCallMenuVisibility();
    }

    /**
     * Atualiza o conteúdo do grid de controle de chamada e mostra/esconde
     * o container conforme o retorno de InCallMenu.updateItems() — false
     * quando não há nenhuma chamada em que o grid faça sentido (ex.:
     * chamada tocando pura, sem outra em andamento — nesse caso é a
     * onscreen-answer UI quem cobre a interação).
     */
    private void updateInCallMenuVisibility() {
        if (mInCallMenu == null || mInCallMenuContainer == null) return;
        boolean shouldShow = mInCallMenu.updateItems();
        mInCallMenuContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private void onHoldClick() {
        if (VDBG) log("onHoldClick()...");

        final boolean hasActiveCall = activeCall() != null;
        final boolean hasHoldingCall = holdingCall() != null;
        if (VDBG) log("- hasActiveCall = " + hasActiveCall
                      + ", hasHoldingCall = " + hasHoldingCall);
        if (hasActiveCall || hasHoldingCall) {
            PhoneUtils.switchHoldingAndActive();
        }
    }

    /**
     * Atualiza a dica "Press Menu for more options" (só usada em modo
     * paisagem; em retrato a InCallMenu já está sempre visível).
     */
    private void updateMenuButtonHint() {
        if (VDBG) log("updateMenuButtonHint()...");
        boolean hintVisible = ConfigurationHelper.isLandscape()
                && mInCallScreenMode == InCallScreenMode.NORMAL
                && hasAnyCalls();
        if (mMenuButtonHint != null) {
            mMenuButtonHint.setVisibility(hintVisible ? View.VISIBLE : View.GONE);
        }
        if (mCallCard != null) {
            mCallCard.setMenuButtonHintVisible(hintVisible);
        }
    }

    private void handleStartupError(InCallInitStatus status) {
        if (DBG) log("handleStartupError(): status = " + status);

        int errorMessageResId;
        switch (status) {
            case NO_PHONE_NUMBER_SUPPLIED:
                errorMessageResId = R.string.incall_error_no_phone_number_supplied;
                break;
            case CALL_FAILED:
                errorMessageResId = R.string.incall_error_call_failed;
                break;
            case PHONE_NOT_IN_USE:
                // Nada em andamento; simplesmente sai sem mostrar erro.
                finish();
                return;
            default:
                errorMessageResId = R.string.incall_error_call_failed;
                break;
        }

        showGenericErrorDialog(errorMessageResId, true);
    }

    private void showGenericErrorDialog(int resid, boolean isStartupError) {
        CharSequence msg = getResources().getText(resid);
        if (DBG) log("showGenericErrorDialog(" + resid + "): " + msg);

        if (mGenericErrorDialog != null) {
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }

        DialogInterface.OnClickListener clickListener;
        DialogInterface.OnCancelListener cancelListener;
        if (isStartupError) {
            clickListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        bailOutAfterErrorDialog();
                    }
                };
            cancelListener = new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        bailOutAfterErrorDialog();
                    }
                };
        } else {
            clickListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                };
            cancelListener = new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                    }
                };
        }

        mGenericErrorDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, clickListener)
                .setOnCancelListener(cancelListener)
                .create();
        mGenericErrorDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mGenericErrorDialog.show();
    }

    private void bailOutAfterErrorDialog() {
        if (mGenericErrorDialog != null) {
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        if (DBG) log("bailOutAfterErrorDialog(): finishing...");
        finish();
    }

    private void dismissAllDialogs() {
        if (mGenericErrorDialog != null) {
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        if (mSuppServiceFailureDialog != null) {
            mSuppServiceFailureDialog.dismiss();
            mSuppServiceFailureDialog = null;
        }
    }

    /* package */ void internalAnswerCall() {
        if (DBG) log("internalAnswerCall()...");
        PhoneUtils.answerCall();
    }

    /**
     * Answer the ringing call *and* hang up the ongoing call.
     */
    /* package */ void internalAnswerAndEnd() {
        if (DBG) log("internalAnswerAndEnd()...");
        PhoneUtils.answerAndEndActive();
    }

    /**
     * Answer the ringing call, in the special case where both lines are
     * already in use. "Atende a nova, encerra a que estava em andamento",
     * conforme a especificação de UI original.
     */
    /* package */ void internalAnswerCallBothLinesInUse() {
        if (DBG) log("internalAnswerCallBothLinesInUse()...");
        PhoneUtils.answerAndEndActive();
    }

    /**
     * Hang up the ringing call (aka "Don't answer").
     */
    /* package */ void internalHangupRingingCall() {
        if (DBG) log("internalHangupRingingCall()...");
        PhoneUtils.hangupRingingCall();
    }

    private void internalSwapCalls() {
        if (VDBG) log("internalSwapCalls()...");

        // Toda vez que trocamos de chamada, forçamos o dialpad a fechar.
        mDialer.closeDialer(true);
        mDialer.clearDigits();

        PhoneUtils.switchHoldingAndActive();
    }

    //
    // "Manage conference" UI.
    //

    private void initManageConferencePanel() {
        if (VDBG) log("initManageConferencePanel()...");
        if (mManageConferencePanel == null) {
            mManageConferencePanel = (ViewGroup) findViewById(R.id.manageConferencePanel);

            mConferenceTime = (Chronometer) findViewById(R.id.manageConferencePanelHeader);
            mConferenceTime.setFormat(getString(R.string.caller_manage_header));

            mConferenceCallList = new ViewGroup[MAX_CALLERS_IN_CONFERENCE];
            {
                final int[] viewGroupIdList = {R.id.caller0, R.id.caller1, R.id.caller2,
                        R.id.caller3, R.id.caller4};
                for (int i = 0; i < MAX_CALLERS_IN_CONFERENCE; i++) {
                    mConferenceCallList[i] = (ViewGroup) findViewById(viewGroupIdList[i]);
                }
            }

            mButtonManageConferenceDone = (Button) findViewById(R.id.manage_done);
            mButtonManageConferenceDone.setOnClickListener(this);
        }
    }

    /**
     * Sets the current high-level "mode" of the in-call UI.
     */
    private void setInCallScreenMode(InCallScreenMode newMode) {
        if (VDBG) log("setInCallScreenMode: " + newMode);
        mInCallScreenMode = newMode;
        switch (mInCallScreenMode) {
            case MANAGE_CONFERENCE: {
                Call fgCall = activeCall();
                if (fgCall == null || !PhoneUtils.isConferenceCall(fgCall)) {
                    Log.w(LOG_TAG, "MANAGE_CONFERENCE: no active conference call!");
                    setInCallScreenMode(InCallScreenMode.NORMAL);
                    return;
                }
                List<Call> children = fgCall.getChildren();
                if (children == null || children.size() <= 1) {
                    Log.w(LOG_TAG,
                          "MANAGE_CONFERENCE: Bogus TRUE from isConferenceCall(); children = "
                          + children);
                    setInCallScreenMode(InCallScreenMode.NORMAL);
                    return;
                }

                initManageConferencePanel();
                updateManageConferencePanel(children);

                mManageConferencePanel.setVisibility(View.VISIBLE);

                long connectTime = (fgCall.getDetails() != null)
                        ? fgCall.getDetails().getConnectTimeMillis() : 0;
                long callDuration = connectTime > 0
                        ? (System.currentTimeMillis() - connectTime) : 0;
                mConferenceTime.setBase(SystemClock.elapsedRealtime() - callDuration);
                mConferenceTime.start();

                mInCallPanel.setVisibility(View.GONE);
                mDialer.hideDTMFDisplay(true);
                break;
            }

            case CALL_ENDED:
                if (mManageConferencePanel != null) {
                    mManageConferencePanel.setVisibility(View.GONE);
                    mConferenceTime.stop();
                }
                updateMenuButtonHint();

                mInCallPanel.setVisibility(View.VISIBLE);
                mDialer.hideDTMFDisplay(false);
                break;

            case NORMAL:
                mInCallPanel.setVisibility(View.VISIBLE);
                mDialer.hideDTMFDisplay(false);
                if (mManageConferencePanel != null) {
                    mManageConferencePanel.setVisibility(View.GONE);
                    mConferenceTime.stop();
                }
                break;
        }

        updateDialpadVisibility();
    }

    /**
     * @return true se a UI "Manage conference" está visível.
     */
    /* package */ boolean isManageConferenceMode() {
        return (mInCallScreenMode == InCallScreenMode.MANAGE_CONFERENCE);
    }

    /**
     * Updates the "Manage conference" UI based on the specified List of
     * child calls (participantes da conferência).
     */
    private void updateManageConferencePanel(List<Call> children) {
        mNumCallersInConference = children.size();
        if (VDBG) log("updateManageConferencePanel: " + mNumCallersInConference + " callers");

        boolean canSeparate = mNumCallersInConference > 1;

        for (int i = 0; i < MAX_CALLERS_IN_CONFERENCE; i++) {
            Call call = (i < children.size()) ? children.get(i) : null;
            updateManageConferenceRow(i, call, canSeparate);
        }
    }

    private void updateManageConferencePanelIfNecessary() {
        Call fgCall = activeCall();
        if (VDBG) log("updateManageConferencePanel: fgCall " + fgCall + "...");

        if (fgCall == null) {
            if (VDBG) log("==> no active call!");
            setInCallScreenMode(InCallScreenMode.NORMAL);
            InCallInitStatus status = syncWithPhoneState();
            if (status != InCallInitStatus.SUCCESS) {
                if (DBG) log("updateManageConferencePanelIfNecessary: finishing...");
                finish();
            }
            return;
        }

        List<Call> children = fgCall.getChildren();
        int numChildren = (children != null) ? children.size() : 0;
        if (numChildren <= 1) {
            if (VDBG) log("==> foreground call no longer a conference!");
            setInCallScreenMode(InCallScreenMode.NORMAL);
            InCallInitStatus status = syncWithPhoneState();
            if (status != InCallInitStatus.SUCCESS) {
                if (DBG) log("updateManageConferencePanelIfNecessary: finishing...");
                finish();
            }
            return;
        }
        if (numChildren != mNumCallersInConference) {
            if (VDBG) log("==> Conference size has changed; need to rebuild UI!");
            updateManageConferencePanel(children);
        }
    }

    /**
     * Updates a single row of the "Manage conference" UI.
     *
     * @param i the row to update
     * @param call the Call corresponding to this caller, ou null se essa
     *        linha deve ficar vazia.
     * @param canSeparate if true, show a "Separate" button on this row.
     */
    private void updateManageConferenceRow(final int i, final Call call, boolean canSeparate) {
        if (VDBG) log("updateManageConferenceRow(" + i + ")...  call = " + call);

        if (call != null) {
            mConferenceCallList[i].setVisibility(View.VISIBLE);

            ImageButton endButton = (ImageButton) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerDisconnect);
            ImageButton separateButton = (ImageButton) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerSeparate);
            TextView nameTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerName);
            TextView numberTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerNumber);
            TextView numberTypeTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerNumberType);

            if (VDBG) log("- button: " + endButton + ", nameTextView: " + nameTextView);

            View.OnClickListener endThisCall = new View.OnClickListener() {
                    public void onClick(View v) {
                        endConferenceCall(i, call);
                        PhoneApp.getInstance().pokeUserActivity();
                    }
                };
            endButton.setOnClickListener(endThisCall);

            if (canSeparate) {
                View.OnClickListener separateThisCall = new View.OnClickListener() {
                        public void onClick(View v) {
                            separateConferenceCall(i, call);
                            PhoneApp.getInstance().pokeUserActivity();
                        }
                    };
                separateButton.setOnClickListener(separateThisCall);
                separateButton.setVisibility(View.VISIBLE);
            } else {
                separateButton.setVisibility(View.INVISIBLE);
            }

            // Nome/número deste participante.
            CallerInfo info = PhoneUtils.getCallerInfo(this, call);
            displayCallerInfoForConferenceRow(info, nameTextView,
                    numberTypeTextView, numberTextView);

            // Consulta assíncrona pra atualizar se o nome do contato
            // ainda não tinha sido resolvido.
            PhoneUtils.startGetCallerInfo(this, call, this, mConferenceCallList[i]);
        } else {
            mConferenceCallList[i].setVisibility(View.GONE);
        }
    }

    /**
     * Implementado para CallerInfoAsyncQuery.OnQueryCompleteListener —
     * atualiza a linha correspondente do painel de conferência quando a
     * consulta assíncrona volta.
     */
    @Override
    public void onQueryComplete(CallerInfo ci, Object cookie) {
        if (VDBG) log("callerinfo query complete, updating UI.");

        if (!(cookie instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) cookie;
        vg.setVisibility(View.VISIBLE);

        displayCallerInfoForConferenceRow(ci,
                (TextView) vg.findViewById(R.id.conferenceCallerName),
                (TextView) vg.findViewById(R.id.conferenceCallerNumberType),
                (TextView) vg.findViewById(R.id.conferenceCallerNumber));
    }

    /**
     * Helper function to fill out the Conference Call(er) information for
     * each item in the "Manage Conference Call" list.
     */
    private void displayCallerInfoForConferenceRow(CallerInfo ci, TextView nameTextView,
            TextView numberTypeTextView, TextView numberTextView) {

        String callerName = "";
        String callerNumber = "";
        String callerNumberType = "";
        if (ci != null) {
            callerName = ci.name;
            if (TextUtils.isEmpty(callerName)) {
                callerName = ci.phoneNumber;
                if (TextUtils.isEmpty(callerName)) {
                    callerName = getString(R.string.unknown);
                }
            } else {
                callerNumber = ci.phoneNumber;
                callerNumberType = ci.phoneLabel;
            }
        }

        nameTextView.setText(callerName);

        if (TextUtils.isEmpty(callerNumber)) {
            numberTextView.setVisibility(View.GONE);
            numberTypeTextView.setVisibility(View.GONE);
        } else {
            numberTextView.setVisibility(View.VISIBLE);
            numberTextView.setText(callerNumber);
            numberTypeTextView.setVisibility(View.VISIBLE);
            numberTypeTextView.setText(callerNumberType);
        }
    }

    /**
     * Ends the specified call on a conference call.
     */
    private void endConferenceCall(int i, Call call) {
        if (VDBG) log("===> ENDING conference call " + i + ": Call " + call);
        PhoneUtils.hangup(call);
        // A UI se atualiza sozinha quando onCallRemoved() chegar.
    }

    /**
     * Separates out the specified call from a conference call.
     */
    private void separateConferenceCall(int i, Call call) {
        if (VDBG) log("===> SEPARATING conference call " + i + ": Call " + call);
        PhoneUtils.separateCall(call);
        // A chamada separada vira automaticamente a chamada em primeiro
        // plano; não precisa de troca manual aqui.
    }

    /**
     * Updates the visibility of the DTMF dialpad and the "sliding drawer"
     * handle, based on the current state of the phone and/or the current
     * InCallScreenMode.
     */
    private void updateDialpadVisibility() {
        // (1) O dialpad em si: se uma chamada está tocando, garante que
        // está fechado (pra não cobrir a UI de chamada recebida).
        if (hasRingingCall()) {
            mDialer.closeDialer(false);
            mDialer.clearDigits();
        }

        // (2) O handle da "gaveta deslizante": só visível se for OK abrir
        // o dialpad agora.
        if (mDialerDrawer != null) {
            int visibility = okToShowDialpad() ? View.VISIBLE : View.GONE;
            mDialerDrawer.setVisibility(visibility);
        }
    }

    /* package */ boolean isDialerOpened() {
        return (mDialer != null && mDialer.isOpened());
    }

    /* package */ void onDialerOpen() {
        if (VDBG) log("onDialerOpen()...");
        resetTouchLockTimer();
        PhoneApp.getInstance().pokeUserActivity();
    }

    /* package */ void onDialerClose() {
        if (VDBG) log("onDialerClose()...");
        enableTouchLock(false);
        PhoneApp.getInstance().pokeUserActivity();
    }

    /* package */ EditText getDialerDisplay() {
        return mDTMFDisplay;
    }

    /**
     * Determines when we can dial DTMF tones.
     */
    private boolean okToDialDTMFTones() {
        final boolean hasRingingCall = hasRingingCall();
        Call fgCall = activeCall();
        final int fgCallState = (fgCall != null) ? fgCall.getState() : Call.STATE_DISCONNECTED;

        boolean canDial =
            (fgCallState == Call.STATE_ACTIVE || fgCallState == Call.STATE_DIALING)
            && !hasRingingCall
            && (mInCallScreenMode != InCallScreenMode.MANAGE_CONFERENCE);

        if (VDBG) log("[okToDialDTMFTones] foreground state: " + fgCallState +
                ", ringing state: " + hasRingingCall +
                ", call screen mode: " + mInCallScreenMode +
                ", result: " + canDial);

        return canDial;
    }

    /* package */ boolean okToShowDialpad() {
        return !ConfigurationHelper.isLandscape() && okToDialDTMFTones();
    }

    /**
     * Initializes the onscreen "answer" UI. Mantido como no-op de UI que
     * só ativa em builds/dispositivos que definirem esse recurso — em
     * qualquer aparelho normal moderno (com tecla CALL virtual do próprio
     * sistema), o ViewStub simplesmente nunca é inflado.
     */
    private void initOnscreenAnswerUi() {
        // Sem sinal confiável de "hardware sem tecla CALL" numa API
        // pública nesta arquitetura; deixamos o container do
        // incall_screen.xml (ViewStub onscreenAnswerUiStub) disponível,
        // mas não inflado por padrão. Se quiser sempre habilitar essa UI,
        // troque a condição abaixo para "true".
        boolean allowOnscreenAnswerUi = false;

        if (allowOnscreenAnswerUi) {
            ViewStub stub = (ViewStub) findViewById(R.id.onscreenAnswerUiStub);
            mOnscreenAnswerUiContainer = stub.inflate();

            mOnscreenAnswerButton = findViewById(R.id.onscreenAnswerButton);
            mOnscreenAnswerButton.setOnTouchListener(this);
        }
    }

    private void updateOnscreenAnswerUi() {
        if (mOnscreenAnswerUiContainer != null) {
            if (hasRingingCall()) {
                mOnscreenAnswerUiContainer.setVisibility(View.VISIBLE);
            } else {
                mOnscreenAnswerUiContainer.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Helper class to manage the (small number of) manual layout and UI
     * changes needed by the in-call UI when switching between landscape
     * and portrait mode.
     */
    /* package */ static class ConfigurationHelper {
        private ConfigurationHelper() {
        }

        private static int sOrientation = Configuration.ORIENTATION_PORTRAIT;

        static boolean isLandscape() {
            return sOrientation == Configuration.ORIENTATION_LANDSCAPE;
        }

        static void initConfiguration(Configuration config) {
            sOrientation = config.orientation;
        }

        static void applyConfigurationToLayout(InCallScreen inCallScreen) {
            // Busca o campo de exibição de DTMF, que só existe (com esse
            // id) no layout landscape (dtmf_dialer_display.xml em
            // res/layout-land); em retrato o include equivalente fica
            // vazio e findViewById retorna null aqui, o que é esperado.
            inCallScreen.mDTMFDisplay =
                    (EditText) inCallScreen.findViewById(R.id.dtmfDialerField);

            // Nota: o antigo ajuste manual de margens/tamanho de fonte em
            // paisagem (CallCard.updateForLandscapeMode()) não existe
            // mais no CallCard desta arquitetura — os recursos alternativos
            // em res/layout-land já cobrem o layout paisagem sem precisar
            // de patch manual via código.
        }
    }

    public boolean isPhoneStateRestricted() {
        return false;
    }

    /**
     * Mantido como fallback de compatibilidade para teclados físicos
     * residuais com tecla MENU — mas o grid de controle da chamada NÃO
     * depende mais disto (ver initInCallScreen()).
     */
    @Override
    public View onCreatePanelView(int featureId) {
        return null;
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        // no-op — o grid de controle já fica sempre visível.
    }

    private void initTouchLock() {
        if (VDBG) log("initTouchLock()...");
        if (mTouchLockOverlay != null) {
            Log.w(LOG_TAG, "initTouchLock: already initialized!");
            return;
        }

        mTouchLockOverlay = findViewById(R.id.touchLockOverlay);
        mTouchLockIcon = findViewById(R.id.touchLockIcon);

        mTouchLockOverlay.setOnTouchListener(this);
        mTouchLockIcon.setOnTouchListener(this);

        mTouchLockFadeIn = AnimationUtils.loadAnimation(this, R.anim.touch_lock_fade_in);
    }

    private boolean isTouchLocked() {
        return (mTouchLockOverlay != null) && (mTouchLockOverlay.getVisibility() == View.VISIBLE);
    }

    /**
     * Enables or disables the "touch lock" overlay on top of the DTMF dialpad.
     */
    private void enableTouchLock(boolean enable) {
        if (VDBG) log("enableTouchLock(" + enable + ")...");
        if (enable) {
            if (!mDialer.isOpened()) {
                if (VDBG) log("enableTouchLock: dialpad isn't up, no need to lock screen.");
                return;
            }
            if (PhoneUtils.isSpeakerOn(getApplicationContext())) {
                if (VDBG) log("enableTouchLock: speaker is on, no need to lock screen.");
                return;
            }
            if (mTouchLockOverlay == null) {
                initTouchLock();
            }

            mTouchLockOverlay.setVisibility(View.VISIBLE);
            mTouchLockOverlay.startAnimation(mTouchLockFadeIn);
        } else {
            if (mTouchLockOverlay != null) mTouchLockOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * Schedule the "touch lock" overlay to begin fading in after a short
     * delay, but only if the DTMF dialpad is currently visible.
     */
    private void resetTouchLockTimer() {
        if (VDBG) log("resetTouchLockTimer()...");
        mHandler.removeMessages(TOUCH_LOCK_TIMER);
        if (mDialer.isOpened() && !isTouchLocked()) {
            mHandler.sendEmptyMessageDelayed(TOUCH_LOCK_TIMER, TOUCH_LOCK_DELAY_DEFAULT);
        }
    }

    private void touchLockTimerExpired() {
        enableTouchLock(true);
    }

    // View.OnTouchListener implementation
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (VDBG) log("onTouch(View " + v + ")...");

        if ((v == mTouchLockIcon) || (v == mTouchLockOverlay)) {
            if (!isTouchLocked()) {
                return false;
            }

            if (v == mTouchLockIcon) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    long now = SystemClock.uptimeMillis();
                    if (VDBG) log("- touch lock icon: handling a DOWN event, t = " + now);

                    if (now < mTouchLockLastTouchTime + ViewConfiguration.getDoubleTapTimeout()) {
                        if (VDBG) log("==> touch lock icon: DOUBLE-TAP!");
                        enableTouchLock(false);
                        resetTouchLockTimer();
                        PhoneApp.getInstance().pokeUserActivity();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mTouchLockLastTouchTime = SystemClock.uptimeMillis();
                }
                return true;
            } else {
                return true;
            }

        } else if (v == mOnscreenAnswerButton) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long now = SystemClock.uptimeMillis();
                if (DBG) log("- onscreen answer button: handling a DOWN event, t = " + now);

                if (now < mOnscreenAnswerButtonLastTouchTime
                        + ViewConfiguration.getDoubleTapTimeout()) {
                    if (DBG) log("==> onscreen answer button: DOUBLE-TAP!");

                    if (hasRingingCall()) {
                        boolean hasActiveCall = activeCall() != null;
                        boolean hasHoldingCall = holdingCall() != null;
                        if (hasActiveCall && hasHoldingCall) {
                            internalAnswerCallBothLinesInUse();
                        } else {
                            internalAnswerCall();
                        }
                    } else {
                        if (DBG) log("onscreen answer button: no ringing call (any more); ignoring...");
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mOnscreenAnswerButtonLastTouchTime = SystemClock.uptimeMillis();
            }
            return true;

        } else {
            Log.w(LOG_TAG, "onTouch: event from unexpected View: " + v);
            return false;
        }
    }

    @Override
    public void onUserInteraction() {
        if (mDialer.isOpened() && !isTouchLocked()) {
            resetTouchLockTimer();
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
