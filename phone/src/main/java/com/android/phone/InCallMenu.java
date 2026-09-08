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
 *
 * ============================================================================
 * ADAPTADO (Rota A / DonutCallManager) — mudanças em relação ao 1.6 original:
 *
 * 1. ESTE É O CONTROLE DA CHAMADA (atender, encerrar, mudo, viva-voz,
 *    espera, trocar/mesclar chamadas) — no Donut original ele só aparecia
 *    ao apertar a tecla física MENU (onCreatePanelView). Aparelhos
 *    modernos não têm essa tecla, então a InCallScreen precisa criar este
 *    objeto e mostrar a InCallMenuView SEMPRE, fixa na tela, em vez de
 *    esperar um keypress. Sem isso a tela de chamada fica sem nenhum
 *    controle visível — nem pra desligar.
 * 2. Não existe mais com.android.internal.telephony.{Call,Phone} — todo
 *    estado agora vem do DonutCallManager (android.telecom.Call).
 * 3. Distinção GSM/CDMA removida (não temos mais phone.getPhoneName());
 *    o layout usado é sempre o "GSM-like" (hold / answerAndHold /
 *    answerAndEnd), que é o caminho comum em qualquer rede moderna.
 * 4. ContextThemeWrapper com o tema interno Theme_IconMenu foi trocado
 *    pelo Context normal da Activity — sem tema de sistema pra puxar.
 * 5. "Bluetooth" fica sempre visível, habilitado quando o sistema reporta
 *    uma rota de áudio Bluetooth disponível (CallAudioState, API pública) --
 *    o antigo BluetoothHandsfree (stack de AT commands rodada pelo próprio
 *    app) foi excluído do build, ver PhoneUtils/DonutCallManager.
 * ============================================================================
 */

package com.android.phone;

import android.content.Context;
import android.telecom.Call;
import android.util.Log;

import com.android.phone.telecom.DonutCallManager;

/**
 * Helper class to manage the call-control grid for the InCallScreen.
 *
 * This class is the "Model" (M-V-C) para o grid de controle de chamada;
 * conhece todos os botões possíveis e decide estado/visibilidade de cada
 * um com base no estado atual das chamadas (DonutCallManager). As classes
 * de View correspondentes são InCallMenuView (layout do grid) e
 * InCallMenuItemView (um botão).
 */
class InCallMenu {
    private static final String LOG_TAG = "PHONE/InCallMenu";
    private static final boolean DBG = false;

    private InCallScreen mInCallScreen;
    private InCallMenuView mInCallMenuView;

    InCallMenuItemView mManageConference;
    InCallMenuItemView mShowDialpad;
    InCallMenuItemView mEndCall;
    InCallMenuItemView mAddCall;
    InCallMenuItemView mSwapCalls;
    InCallMenuItemView mMergeCalls;
    InCallMenuItemView mBluetooth;
    InCallMenuItemView mSpeaker;
    InCallMenuItemView mMute;
    InCallMenuItemView mHold;
    InCallMenuItemView mAnswerAndHold;
    InCallMenuItemView mAnswerAndEnd;
    InCallMenuItemView mAnswer;
    InCallMenuItemView mIgnore;

    InCallMenu(InCallScreen inCallScreen) {
        mInCallScreen = inCallScreen;
    }

    void clearInCallScreenReference() {
        mInCallScreen = null;
        if (mInCallMenuView != null) mInCallMenuView.clearInCallScreenReference();
    }

    /* package */ InCallMenuView getView() {
        return mInCallMenuView;
    }

    /**
     * Cria a InCallMenuView e todos os botões possíveis. Chamado uma única
     * vez pela InCallScreen — hoje logo no onCreate/initInCallScreen (não
     * mais "na primeira vez que a tecla MENU é apertada", já que essa
     * tecla não existe mais: o grid precisa estar pronto e visível desde
     * o primeiro frame da tela de chamada).
     */
    /* package */ void initMenu() {
        if (DBG) log("initMenu()...");

        Context context = mInCallScreen;
        mInCallMenuView = new InCallMenuView(context, mInCallScreen);

        mManageConference = new InCallMenuItemView(context);
        mManageConference.setId(R.id.menuManageConference);
        mManageConference.setOnClickListener(mInCallScreen);
        mManageConference.setText(R.string.menu_manageConference);

        mShowDialpad = new InCallMenuItemView(context);
        mShowDialpad.setId(R.id.menuShowDialpad);
        mShowDialpad.setOnClickListener(mInCallScreen);
        mShowDialpad.setText(R.string.menu_showDialpad);
        mShowDialpad.setIconResource(R.drawable.ic_menu_dial_pad);

        mEndCall = new InCallMenuItemView(context);
        mEndCall.setId(R.id.menuEndCall);
        mEndCall.setOnClickListener(mInCallScreen);
        mEndCall.setText(R.string.menu_endCall);
        mEndCall.setIconResource(R.drawable.ic_menu_end_call);

        mAddCall = new InCallMenuItemView(context);
        mAddCall.setId(R.id.menuAddCall);
        mAddCall.setOnClickListener(mInCallScreen);
        mAddCall.setText(R.string.menu_addCall);
        mAddCall.setIconResource(android.R.drawable.ic_menu_add);

        mSwapCalls = new InCallMenuItemView(context);
        mSwapCalls.setId(R.id.menuSwapCalls);
        mSwapCalls.setOnClickListener(mInCallScreen);
        mSwapCalls.setText(R.string.menu_swapCalls);
        mSwapCalls.setIconResource(R.drawable.ic_menu_swap_calls);

        mMergeCalls = new InCallMenuItemView(context);
        mMergeCalls.setId(R.id.menuMergeCalls);
        mMergeCalls.setOnClickListener(mInCallScreen);
        mMergeCalls.setText(R.string.menu_mergeCalls);
        mMergeCalls.setIconResource(R.drawable.ic_menu_merge_calls);

        mBluetooth = new InCallMenuItemView(context);
        mBluetooth.setId(R.id.menuBluetooth);
        mBluetooth.setOnClickListener(mInCallScreen);
        mBluetooth.setText(R.string.menu_bluetooth);
        mBluetooth.setIndicatorVisible(true);

        mSpeaker = new InCallMenuItemView(context);
        mSpeaker.setId(R.id.menuSpeaker);
        mSpeaker.setOnClickListener(mInCallScreen);
        mSpeaker.setText(R.string.menu_speaker);
        mSpeaker.setIndicatorVisible(true);

        mMute = new InCallMenuItemView(context);
        mMute.setId(R.id.menuMute);
        mMute.setOnClickListener(mInCallScreen);
        mMute.setText(R.string.menu_mute);
        mMute.setIndicatorVisible(true);

        mHold = new InCallMenuItemView(context);
        mHold.setId(R.id.menuHold);
        mHold.setOnClickListener(mInCallScreen);
        mHold.setText(R.string.menu_hold);
        mHold.setIndicatorVisible(true);

        mAnswerAndHold = new InCallMenuItemView(context);
        mAnswerAndHold.setId(R.id.menuAnswerAndHold);
        mAnswerAndHold.setOnClickListener(mInCallScreen);
        mAnswerAndHold.setText(R.string.menu_answerAndHold);

        mAnswerAndEnd = new InCallMenuItemView(context);
        mAnswerAndEnd.setId(R.id.menuAnswerAndEnd);
        mAnswerAndEnd.setOnClickListener(mInCallScreen);
        mAnswerAndEnd.setText(R.string.menu_answerAndEnd);

        mAnswer = new InCallMenuItemView(context);
        mAnswer.setId(R.id.menuAnswer);
        mAnswer.setOnClickListener(mInCallScreen);
        mAnswer.setText(R.string.menu_answer);

        mIgnore = new InCallMenuItemView(context);
        mIgnore.setId(R.id.menuIgnore);
        mIgnore.setOnClickListener(mInCallScreen);
        mIgnore.setText(R.string.menu_ignore);

        // Row 0: dialpad / manage conference
        mInCallMenuView.addItemView(mShowDialpad, 0);
        mInCallMenuView.addItemView(mManageConference, 0);

        // Row 1: swap / merge / add / end
        mInCallMenuView.addItemView(mSwapCalls, 1);
        mInCallMenuView.addItemView(mMergeCalls, 1);
        mInCallMenuView.addItemView(mAddCall, 1);
        mInCallMenuView.addItemView(mEndCall, 1);

        // Row 2: hold/answer variants + mute/speaker/bluetooth
        mInCallMenuView.addItemView(mHold, 2);
        mInCallMenuView.addItemView(mAnswerAndHold, 2);
        mInCallMenuView.addItemView(mAnswerAndEnd, 2);
        mInCallMenuView.addItemView(mAnswer, 2);
        mInCallMenuView.addItemView(mIgnore, 2);
        mInCallMenuView.addItemView(mMute, 2);
        mInCallMenuView.addItemView(mSpeaker, 2);
        mInCallMenuView.addItemView(mBluetooth, 2);

        mInCallMenuView.dumpState();
    }

    /**
     * Atualiza o estado/visibilidade de todos os botões com base no
     * estado atual das chamadas (DonutCallManager). Chamado sempre que
     * algo muda — não só "antes de mostrar o menu", já que agora ele
     * está sempre visível.
     *
     * @return true se o grid deve ficar visível; false se não há nenhuma
     *         chamada (tela ociosa) e o grid deve sumir.
     */
    /* package */ boolean updateItems() {
        if (DBG) log("updateItems()...");

        DonutCallManager mgr = DonutCallManager.getInstance();
        java.util.List<Call> calls = mgr.getCalls();

        if (calls.isEmpty()) {
            return false;
        }

        Call ringingCall = null;
        Call activeCall = null;
        Call holdingCall = null;
        for (Call c : calls) {
            switch (c.getState()) {
                case Call.STATE_RINGING:
                    ringingCall = c;
                    break;
                case Call.STATE_HOLDING:
                    holdingCall = c;
                    break;
                case Call.STATE_ACTIVE:
                case Call.STATE_DIALING:
                case Call.STATE_CONNECTING:
                    activeCall = c;
                    break;
                default:
                    break;
            }
        }

        final boolean hasRingingCall = ringingCall != null;
        final boolean hasActiveCall = activeCall != null;
        final boolean hasHoldingCall = holdingCall != null;

        if (hasRingingCall) {
            if (hasActiveCall || hasHoldingCall) {
                // Chamada em espera chegando (call waiting): só os botões
                // de atender/encerrar-e-atender fazem sentido.
                mAnswerAndHold.setVisible(true);
                mAnswerAndHold.setEnabled(true);
                mAnswerAndEnd.setVisible(true);
                mAnswerAndEnd.setEnabled(true);

                mAnswer.setVisible(false);
                mIgnore.setVisible(false);
                mManageConference.setVisible(false);
                mShowDialpad.setVisible(false);
                mEndCall.setVisible(false);
                mAddCall.setVisible(false);
                mSwapCalls.setVisible(false);
                mMergeCalls.setVisible(false);
                mBluetooth.setVisible(false);
                mSpeaker.setVisible(false);
                mMute.setVisible(false);
                mHold.setVisible(false);

                mInCallMenuView.updateVisibility();
                return true;
            } else {
                // Chamada tocando pura (sem outra chamada em andamento):
                // atender/recusar puros. Deixa a InCallScreen desenhar o
                // onscreen_answer_ui pra isso; o grid fica escondido.
                return false;
            }
        }

        boolean canManageConference = PhoneUtils.isConferenceCall(activeCall);
        mManageConference.setVisible(canManageConference);
        mManageConference.setEnabled(mInCallScreen == null
                || !mInCallScreen.isManageConferenceMode());

        boolean showShowDialpad = !canManageConference;
        mShowDialpad.setVisible(showShowDialpad);
        mShowDialpad.setEnabled(showShowDialpad);

        mEndCall.setVisible(true);
        mEndCall.setEnabled(true);

        mAddCall.setVisible(true);
        mAddCall.setEnabled(PhoneUtils.okToAddCall());

        boolean canSwap = PhoneUtils.okToSwapCalls();
        boolean canMerge = PhoneUtils.okToMergeCalls();
        mSwapCalls.setVisible(true);
        mSwapCalls.setEnabled(canSwap);
        mMergeCalls.setVisible(true);
        mMergeCalls.setEnabled(canMerge);

        // Bluetooth: roteamento de áudio via CallAudioState (API pública) --
        // habilitado só quando o sistema reporta uma rota Bluetooth
        // disponível (fone/carro pareado e conectado); ver PhoneUtils/
        // DonutCallManager para o porquê do antigo BluetoothHandsfree ter
        // sido excluído do build.
        boolean bluetoothAvailable = PhoneUtils.isBluetoothAvailable();
        mBluetooth.setVisible(true);
        mBluetooth.setEnabled(bluetoothAvailable);
        mBluetooth.setIndicatorState(bluetoothAvailable && PhoneUtils.isBluetoothAudioOn());

        mSpeaker.setVisible(true);
        mSpeaker.setEnabled(true);
        mSpeaker.setIndicatorState(PhoneUtils.isSpeakerOn(mInCallScreen));

        mMute.setVisible(true);
        boolean muteOn = PhoneUtils.getMute();
        boolean canMute = hasActiveCall;
        mMute.setIndicatorState(muteOn);
        mMute.setEnabled(canMute);

        mHold.setVisible(true);
        boolean onHold = hasHoldingCall && !hasActiveCall;
        boolean canHold = hasActiveCall || hasHoldingCall;
        mHold.setIndicatorState(onHold);
        mHold.setEnabled(canHold);

        mAnswer.setVisible(false);
        mAnswer.setEnabled(false);
        mIgnore.setVisible(false);
        mIgnore.setEnabled(false);
        mAnswerAndHold.setVisible(false);
        mAnswerAndHold.setEnabled(false);
        mAnswerAndEnd.setVisible(false);
        mAnswerAndEnd.setEnabled(false);

        mInCallMenuView.updateVisibility();
        return true;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
