/*
 * Copyright (C) 2008 The Android Open Source Project
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
 */

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Tela de discagem própria do módulo Phone.
 *
 * NOTA: o TwelveKeyDialer original do Donut foi movido pro app Contacts (ver
 * comentário no topo de EmergencyDialer.java) -- o Phone nunca teve a sua
 * própria tela de discar por toque. Ela é necessária agora porque o Android
 * moderno só oferece o papel de discador padrão (RoleManager.ROLE_DIALER,
 * que dá acesso a chamada de emergência com um toque, ID de chamada, etc.)
 * pra um app que tenha, no mesmo pacote, tanto uma Activity ACTION_DIAL
 * quanto um InCallService (o Phone já tem o DonutInCallService). Sem essa
 * tela, o Phone continua funcional como app de chamada em andamento, mas o
 * sistema nunca oferece ele como opção de discador padrão.
 *
 * Estrutura e boa parte do código de teclado/DTMF copiados de
 * EmergencyDialer.java, removendo a restrição de "só número de emergência"
 * e discando por ACTION_CALL normal (passa pelo OutgoingCallBroadcaster já
 * registrado no manifest, que decide sozinho se é emergência ou não).
 */
public class PhoneDialerActivity extends Activity implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener, TextWatcher {

    private static final String LAST_NUMBER = "lastNumber";
    private static final boolean DBG = false;
    private static final String LOG_TAG = "PhoneDialerActivity";

    private static final int STOP_TONE = 1;
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_RELATIVE_VOLUME = 50;
    private static final int REQUEST_CODE_CALL_PHONE = 1;

    private EditText mDigits;
    private View mDelete;
    private View mCallButton;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();

    private Drawable mDigitsBackground;
    private Drawable mDigitsEmptyBackground;
    private Drawable mDeleteBackground;
    private Drawable mDeleteEmptyBackground;

    private boolean mDTMFToneEnabled;
    private String mLastNumber;

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        // Do nothing
    }

    public void afterTextChanged(Editable input) {
        if (SpecialCharSequenceMgr.handleChars(this, input.toString(), this)) {
            mDigits.getText().delete(0, mDigits.getText().length());
        }

        if (mDigits.length() != 0) {
            mDelete.setBackgroundDrawable(mDeleteBackground);
            mDigits.setBackgroundDrawable(mDigitsBackground);
            mDigits.setCompoundDrawablesWithIntrinsicBounds(
                    getResources().getDrawable(R.drawable.ic_dial_number), null, null, null);
        } else {
            mDelete.setBackgroundDrawable(mDeleteEmptyBackground);
            mDigits.setBackgroundDrawable(mDigitsEmptyBackground);
            mDigits.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.phone_dialer);

        Resources r = getResources();
        mDigitsBackground = r.getDrawable(R.drawable.btn_dial_textfield_active);
        mDigitsEmptyBackground = r.getDrawable(R.drawable.btn_dial_textfield);
        mDeleteBackground = r.getDrawable(R.drawable.btn_dial_delete_active);
        mDeleteEmptyBackground = r.getDrawable(R.drawable.btn_dial_delete);

        mDigits = (EditText) findViewById(R.id.digits);
        mDigits.setKeyListener(DialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setLongClickable(false);

        View view = findViewById(R.id.one);
        if (view != null) {
            setupKeypad();
        }

        mDelete = findViewById(R.id.backspace);
        mDelete.setOnClickListener(this);
        mDelete.setOnLongClickListener(this);

        mCallButton = findViewById(R.id.callButton);
        if (mCallButton != null) {
            mCallButton.setOnClickListener(this);
        }

        // Se a Activity foi aberta via ACTION_DIAL/ACTION_VIEW com um número
        // (ex: tel:11987654321, ou clique em "Ligar" vindo do Contacts),
        // pré-preenche o campo de dígitos em vez de abrir vazia.
        String number = PhoneNumberUtils.getNumberFromIntent(getIntent(), this);
        if (!TextUtils.isEmpty(number)) {
            mDigits.setText(number);
        }

        if (icicle != null) {
            super.onRestoreInstanceState(icicle);
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(AudioManager.STREAM_RING,
                            TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneStopper.removeMessages(STOP_TONE);
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        mLastNumber = icicle.getString(LAST_NUMBER);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LAST_NUMBER, mLastNumber);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDigits.addTextChangedListener(this);
    }

    private void setupKeypad() {
        findViewById(R.id.one).setOnClickListener(this);
        findViewById(R.id.two).setOnClickListener(this);
        findViewById(R.id.three).setOnClickListener(this);
        findViewById(R.id.four).setOnClickListener(this);
        findViewById(R.id.five).setOnClickListener(this);
        findViewById(R.id.six).setOnClickListener(this);
        findViewById(R.id.seven).setOnClickListener(this);
        findViewById(R.id.eight).setOnClickListener(this);
        findViewById(R.id.nine).setOnClickListener(this);
        findViewById(R.id.star).setOnClickListener(this);

        View view = findViewById(R.id.zero);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);

        findViewById(R.id.pound).setOnClickListener(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (TextUtils.isEmpty(mDigits.getText().toString())) {
                    finish();
                } else {
                    placeCall();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void keyPressed(int keyCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);
    }

    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    placeCall();
                    return true;
                }
                break;
        }
        return false;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.one: {
                playTone(ToneGenerator.TONE_DTMF_1);
                keyPressed(KeyEvent.KEYCODE_1);
                return;
            }
            case R.id.two: {
                playTone(ToneGenerator.TONE_DTMF_2);
                keyPressed(KeyEvent.KEYCODE_2);
                return;
            }
            case R.id.three: {
                playTone(ToneGenerator.TONE_DTMF_3);
                keyPressed(KeyEvent.KEYCODE_3);
                return;
            }
            case R.id.four: {
                playTone(ToneGenerator.TONE_DTMF_4);
                keyPressed(KeyEvent.KEYCODE_4);
                return;
            }
            case R.id.five: {
                playTone(ToneGenerator.TONE_DTMF_5);
                keyPressed(KeyEvent.KEYCODE_5);
                return;
            }
            case R.id.six: {
                playTone(ToneGenerator.TONE_DTMF_6);
                keyPressed(KeyEvent.KEYCODE_6);
                return;
            }
            case R.id.seven: {
                playTone(ToneGenerator.TONE_DTMF_7);
                keyPressed(KeyEvent.KEYCODE_7);
                return;
            }
            case R.id.eight: {
                playTone(ToneGenerator.TONE_DTMF_8);
                keyPressed(KeyEvent.KEYCODE_8);
                return;
            }
            case R.id.nine: {
                playTone(ToneGenerator.TONE_DTMF_9);
                keyPressed(KeyEvent.KEYCODE_9);
                return;
            }
            case R.id.zero: {
                playTone(ToneGenerator.TONE_DTMF_0);
                keyPressed(KeyEvent.KEYCODE_0);
                return;
            }
            case R.id.pound: {
                playTone(ToneGenerator.TONE_DTMF_P);
                keyPressed(KeyEvent.KEYCODE_POUND);
                return;
            }
            case R.id.star: {
                playTone(ToneGenerator.TONE_DTMF_S);
                keyPressed(KeyEvent.KEYCODE_STAR);
                return;
            }
            case R.id.digits: {
                placeCall();
                return;
            }
            case R.id.backspace: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                return;
            }
            case R.id.callButton: {
                placeCall();
                return;
            }
        }
    }

    public boolean onLongClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.backspace: {
                mDigits.getText().delete(0, mDigits.getText().length());
                return true;
            }
            case R.id.zero: {
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        mDTMFToneEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(AudioManager.STREAM_RING,
                            TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneStopper.removeMessages(STOP_TONE);
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    /**
     * Dispara a chamada via ACTION_CALL normal (passa pelo
     * OutgoingCallBroadcaster). Diferente do EmergencyDialer, aqui não há
     * restrição de "só número de emergência" -- qualquer número digitado
     * pode ser discado, já que esta é a tela de discagem geral do app.
     *
     * ACTION_CALL exige a permissão CALL_PHONE em runtime (API 23+); se
     * ainda não concedida, pede e guarda o número pra tentar de novo depois
     * que o usuário responder.
     */
    void placeCall() {
        mLastNumber = mDigits.getText().toString();
        if (TextUtils.isEmpty(mLastNumber) || !TextUtils.isGraphic(mLastNumber)) {
            playTone(ToneGenerator.TONE_PROP_NACK);
            return;
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.CALL_PHONE },
                    REQUEST_CODE_CALL_PHONE);
            return;
        }

        if (DBG) Log.d(LOG_TAG, "placing call to " + mLastNumber);
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", mLastNumber, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CALL_PHONE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            placeCall();
        }
    }

    Handler mToneStopper = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case STOP_TONE:
                    synchronized (mToneGeneratorLock) {
                        if (mToneGenerator == null) {
                            Log.w(LOG_TAG, "mToneStopper: mToneGenerator == null");
                        } else {
                            mToneGenerator.stopTone();
                        }
                    }
                    break;
            }
        }
    };

    void playTone(int tone) {
        if (!mDTMFToneEnabled) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(LOG_TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            mToneStopper.removeMessages(STOP_TONE);
            mToneGenerator.startTone(tone);
            mToneStopper.sendEmptyMessageDelayed(STOP_TONE, TONE_LENGTH_MS);
        }
    }
}
