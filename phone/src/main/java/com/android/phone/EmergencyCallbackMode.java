/*
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
import android.content.Intent;
import com.android.phone.compat.TelephonyIntentsCompat;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;

import com.android.phone.compat.NotPortedYet;

/**
 * Phone app Emergency Callback screen.
 *
 * NOTA: mPhone/PhoneFactory foram removidos -- a lógica de "sair do modo
 * callback de emergência" (Phone.exitEmergencyCallbackMode) já vinha
 * COMENTADA no código original de 2009 (nunca chegou a ser ligada). Os
 * botões Dial e OK já usavam só API pública (TelephonyIntentsCompat.ACTION_CALL_EMERGENCY,
 * NotificationMgr) e continuam funcionando; o botão Exit agora mostra a
 * mensagem de "não faço milagre" em vez de ficar mudo sem feedback nenhum.
 */
public class EmergencyCallbackMode extends Activity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ecbm_layout);

        // Watch for button clicks.
        ImageButton dialButton = (ImageButton)findViewById(R.id.button_dial);
        dialButton.setOnClickListener(mDialListener);

        Button exitButton = (Button)findViewById(R.id.button_exit);
        exitButton.setOnClickListener(mExitListener);

        Button okButton = (Button)findViewById(R.id.button_ok);
        okButton.setOnClickListener(mOkListener);

        //cancel ECBM notification
        NotificationMgr.getDefault().cancelEcbmNotification();
    }

    private OnClickListener mDialListener = new OnClickListener()
    {
        public void onClick(View v)
        {
            Intent intent = new Intent(TelephonyIntentsCompat.ACTION_CALL_EMERGENCY,  Uri.parse("tel:911"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            //create Notification
            NotificationMgr.getDefault().notifyECBM();
            // finish Application
            finish();

        }
    };

    private OnClickListener mExitListener = new OnClickListener()
    {
        public void onClick(View v) {
            // Sair do modo callback de emergência (Phone.exitEmergencyCallbackMode)
            // é signature-only, sem equivalente público -- e já estava
            // desligado no código original de 2009 (comentado). Agora ao
            // menos dá feedback em vez de ficar mudo.
            NotPortedYet.show(EmergencyCallbackMode.this);
        }
    };


    private OnClickListener mOkListener = new OnClickListener()
    {
        public void onClick(View v)
        {
            // create a notification
            NotificationMgr.getDefault().notifyECBM();
            // finish Application
            finish();
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // suppress all key presses except of call key
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                Intent intent = new Intent(TelephonyIntentsCompat.ACTION_CALL_EMERGENCY,  Uri.parse("tel:911"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        }
        return true;
    }

}