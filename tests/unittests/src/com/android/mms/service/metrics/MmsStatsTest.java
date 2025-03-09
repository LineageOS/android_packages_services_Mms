/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.mms.service.metrics;

import static com.android.mms.MmsStatsLog.INCOMING_MMS__RESULT__MMS_RESULT_SUCCESS;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_NO_DATA_NETWORK;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS__RESULT__MMS_RESULT_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.flags.Flags;
import com.android.mms.IncomingMms;
import com.android.mms.OutgoingMms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class MmsStatsTest {
    // Mocked classes
    private Context mContext;
    private PersistMmsAtomsStorage mPersistMmsAtomsStorage;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mPersistMmsAtomsStorage = mock(PersistMmsAtomsStorage.class);
        mTelephonyManager = mock(TelephonyManager.class);
        mSubscriptionManager = mock(SubscriptionManager.class);

        doReturn(mSubscriptionManager).when(mContext).getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    @After
    public void tearDown() {
        mContext = null;
        mPersistMmsAtomsStorage = null;
        mTelephonyManager = null;
    }

    @Test
    public void addAtomToStorage_incomingMms_default() {
        doReturn(null).when(mTelephonyManager).getServiceState();
        doReturn(TelephonyManager.UNKNOWN_CARRIER_ID).when(mTelephonyManager).getSimCarrierId();
        int inactiveSubId = 123;
        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, inactiveSubId,
                mTelephonyManager, null, true);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        ArgumentCaptor<IncomingMms> incomingMmsCaptor = ArgumentCaptor.forClass(IncomingMms.class);
        verify(mPersistMmsAtomsStorage).addIncomingMms(incomingMmsCaptor.capture());
        IncomingMms incomingMms = incomingMmsCaptor.getValue();
        assertThat(incomingMms.getRat()).isEqualTo(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat(incomingMms.getResult()).isEqualTo(INCOMING_MMS__RESULT__MMS_RESULT_SUCCESS);
        assertThat(incomingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        assertThat(incomingMms.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(incomingMms.getIsMultiSim()).isEqualTo(false);
        assertThat(incomingMms.getIsEsim()).isEqualTo(false);
        assertThat(incomingMms.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(incomingMms.getMmsCount()).isEqualTo(1);
        assertThat(incomingMms.getRetryId()).isEqualTo(0);
        assertThat(incomingMms.getHandledByCarrierApp()).isEqualTo(false);
        assertThat(incomingMms.getIsManagedProfile()).isEqualTo(false);
        assertThat(incomingMms.getIsNtn()).isEqualTo(false);
        verifyNoMoreInteractions(mPersistMmsAtomsStorage);
    }

    private OutgoingMms addAtomToStorage_outgoingMms(
            int result, int retryId, boolean handledByCarrierApp, long mMessageId) {
        doReturn(null).when(mTelephonyManager).getServiceState();
        doReturn(TelephonyManager.UNKNOWN_CARRIER_ID).when(mTelephonyManager).getSimCarrierId();
        int inactiveSubId = 123;
        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, inactiveSubId,
                mTelephonyManager, null, false);
        mmsStats.addAtomToStorage(result, retryId, handledByCarrierApp, mMessageId);

        ArgumentCaptor<OutgoingMms> outgoingMmsCaptor = ArgumentCaptor.forClass(OutgoingMms.class);
        verify(mPersistMmsAtomsStorage).addOutgoingMms(outgoingMmsCaptor.capture());
        verifyNoMoreInteractions(mPersistMmsAtomsStorage);
        return outgoingMmsCaptor.getValue();
    }

    @Test
    public void addAtomToStorage_outgoingMms_default() {
        OutgoingMms outgoingMms = addAtomToStorage_outgoingMms(Activity.RESULT_OK, 0, false, 0);
        assertThat(outgoingMms.getRat()).isEqualTo(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat(outgoingMms.getResult()).isEqualTo(OUTGOING_MMS__RESULT__MMS_RESULT_SUCCESS);
        assertThat(outgoingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        assertThat(outgoingMms.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(outgoingMms.getIsMultiSim()).isEqualTo(false);
        assertThat(outgoingMms.getIsEsim()).isEqualTo(false);
        assertThat(outgoingMms.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(outgoingMms.getMmsCount()).isEqualTo(1);
        assertThat(outgoingMms.getRetryId()).isEqualTo(0);
        assertThat(outgoingMms.getHandledByCarrierApp()).isEqualTo(false);
        assertThat(outgoingMms.getIsFromDefaultApp()).isEqualTo(false);
        assertThat(outgoingMms.getIsManagedProfile()).isEqualTo(false);
        assertThat(outgoingMms.getIsNtn()).isEqualTo(false);
    }

    @Test
    public void addAtomToStorage_outgoingMms_handledByCarrierApp_Succeeded() {
        OutgoingMms outgoingMms = addAtomToStorage_outgoingMms(Activity.RESULT_OK, 0, true, 0);
        assertThat(outgoingMms.getRat()).isEqualTo(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat(outgoingMms.getResult()).isEqualTo(OUTGOING_MMS__RESULT__MMS_RESULT_SUCCESS);
        assertThat(outgoingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        assertThat(outgoingMms.getSimSlotIndex())
                .isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(outgoingMms.getIsMultiSim()).isEqualTo(false);
        assertThat(outgoingMms.getIsEsim()).isEqualTo(false);
        assertThat(outgoingMms.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(outgoingMms.getMmsCount()).isEqualTo(1);
        assertThat(outgoingMms.getRetryId()).isEqualTo(0);
        assertThat(outgoingMms.getHandledByCarrierApp()).isEqualTo(true);
        assertThat(outgoingMms.getIsFromDefaultApp()).isEqualTo(false);
        assertThat(outgoingMms.getIsManagedProfile()).isEqualTo(false);
        assertThat(outgoingMms.getIsNtn()).isEqualTo(false);
    }

    @Test
    public void addAtomToStorage_outgoingMms_handledByCarrierApp_FailedWithoutReason() {
        OutgoingMms outgoingMms =
                addAtomToStorage_outgoingMms(SmsManager.MMS_ERROR_UNSPECIFIED, 0, true, 0);
        assertThat(outgoingMms.getRat()).isEqualTo(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat(outgoingMms.getResult())
                .isEqualTo(OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_UNSPECIFIED);
        assertThat(outgoingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        assertThat(outgoingMms.getSimSlotIndex())
                .isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(outgoingMms.getIsMultiSim()).isEqualTo(false);
        assertThat(outgoingMms.getIsEsim()).isEqualTo(false);
        assertThat(outgoingMms.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(outgoingMms.getMmsCount()).isEqualTo(1);
        assertThat(outgoingMms.getRetryId()).isEqualTo(0);
        assertThat(outgoingMms.getHandledByCarrierApp()).isEqualTo(true);
        assertThat(outgoingMms.getIsFromDefaultApp()).isEqualTo(false);
        assertThat(outgoingMms.getIsManagedProfile()).isEqualTo(false);
        assertThat(outgoingMms.getIsNtn()).isEqualTo(false);
    }

    @Test
    public void addAtomToStorage_outgoingMms_handledByCarrierApp_FailedWithReason() {
        if (!Flags.temporaryFailuresInCarrierMessagingService()) {
            return;
        }
        OutgoingMms outgoingMms =
                addAtomToStorage_outgoingMms(SmsManager.MMS_ERROR_NO_DATA_NETWORK, 0, true, 0);
        assertThat(outgoingMms.getRat()).isEqualTo(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat(outgoingMms.getResult())
                .isEqualTo(OUTGOING_MMS__RESULT__MMS_RESULT_ERROR_NO_DATA_NETWORK);
        assertThat(outgoingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        assertThat(outgoingMms.getSimSlotIndex())
                .isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(outgoingMms.getIsMultiSim()).isEqualTo(false);
        assertThat(outgoingMms.getIsEsim()).isEqualTo(false);
        assertThat(outgoingMms.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(outgoingMms.getMmsCount()).isEqualTo(1);
        assertThat(outgoingMms.getRetryId()).isEqualTo(0);
        assertThat(outgoingMms.getHandledByCarrierApp()).isEqualTo(true);
        assertThat(outgoingMms.getIsFromDefaultApp()).isEqualTo(false);
        assertThat(outgoingMms.getIsManagedProfile()).isEqualTo(false);
        assertThat(outgoingMms.getIsNtn()).isEqualTo(false);
    }

    @Test
    public void getDataRoamingType_serviceState_notNull() {
        ServiceState serviceState = mock(ServiceState.class);
        doReturn(serviceState).when(mTelephonyManager).getServiceState();
        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, 1,
                mTelephonyManager, null, true);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        ArgumentCaptor<IncomingMms> incomingMmsCaptor = ArgumentCaptor.forClass(IncomingMms.class);
        verify(mPersistMmsAtomsStorage).addIncomingMms(incomingMmsCaptor.capture());
        IncomingMms incomingMms = incomingMmsCaptor.getValue();
        assertThat(incomingMms.getRoaming()).isEqualTo(ServiceState.ROAMING_TYPE_NOT_ROAMING);
    }


    @Test
    public void isDefaultMmsApp_subId_inactive() {
        int inactiveSubId = 123;
        doReturn(false).when(mSubscriptionManager)
                .isActiveSubscriptionId(eq(inactiveSubId));

        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, inactiveSubId,
                mTelephonyManager, null, false);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        // getSubscriptionUserHandle should not be called if subID is inactive.
        verify(mSubscriptionManager, never()).getSubscriptionUserHandle(eq(inactiveSubId));
    }

    @Test
    public void testIsNtn_serviceState_notNull() {
        if (!Flags.carrierEnabledSatelliteFlag()) {
            return;
        }

        ServiceState serviceState = mock(ServiceState.class);
        doReturn(serviceState).when(mTelephonyManager).getServiceState();
        doReturn(true).when(serviceState).isUsingNonTerrestrialNetwork();

        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, 1,
                mTelephonyManager, null, true);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        ArgumentCaptor<IncomingMms> incomingMmsCaptor = ArgumentCaptor.forClass(IncomingMms.class);
        verify(mPersistMmsAtomsStorage).addIncomingMms(incomingMmsCaptor.capture());
        IncomingMms incomingMms = incomingMmsCaptor.getValue();
        assertThat(incomingMms.getIsNtn()).isEqualTo(true);

        reset(mPersistMmsAtomsStorage);
        reset(serviceState);
        doReturn(false).when(serviceState).isUsingNonTerrestrialNetwork();

        mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, 1,
                mTelephonyManager, null, true);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        incomingMmsCaptor = ArgumentCaptor.forClass(IncomingMms.class);
        verify(mPersistMmsAtomsStorage).addIncomingMms(incomingMmsCaptor.capture());
        incomingMms = incomingMmsCaptor.getValue();
        assertThat(incomingMms.getIsNtn()).isEqualTo(false);
    }

    @Test
    public void testIsNtn_serviceState_Null() {
        if (!Flags.carrierEnabledSatelliteFlag()) {
            return;
        }

        doReturn(null).when(mTelephonyManager).getServiceState();

        MmsStats mmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, 1,
                mTelephonyManager, null, true);
        mmsStats.addAtomToStorage(Activity.RESULT_OK);

        ArgumentCaptor<IncomingMms> incomingMmsCaptor = ArgumentCaptor.forClass(IncomingMms.class);
        verify(mPersistMmsAtomsStorage).addIncomingMms(incomingMmsCaptor.capture());
        IncomingMms incomingMms = incomingMmsCaptor.getValue();
        assertThat(incomingMms.getIsNtn()).isEqualTo(false);
    }
}