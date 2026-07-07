/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.mms.service;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.mms.service.metrics.MmsStats;
import com.android.mms.service.metrics.PersistMmsAtomsStorage;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class MmsRequestRoboTest {
    // Mocked classes
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    private final int mSubId = 1;
    private MmsService mMmsService;
    private MmsStats mMmsStats;
    private static final String sFakeUri = "http://greatdogs.com";
    private static final String sFakeLocationUri = "http://greatdogs.com";
    private static final long sFakeMessageId = 8675309L;
    private PersistMmsAtomsStorage mPersistMmsAtomsStorage;
    private SmsManager mSmsManager;
    private Bundle mCarrierConfigValues;
    private static final int sMaxPduSize = 3 * 1000;
    private static final int CALLING_USER = 10;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mTelephonyManager = mock(TelephonyManager.class);
        mSubscriptionManager = mock(SubscriptionManager.class);
        mSmsManager = spy(SmsManager.getSmsManagerForSubscriptionId(mSubId));

        when(mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);
        when(mTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mTelephonyManager);
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mSubscriptionManager);

        mPersistMmsAtomsStorage = mock(PersistMmsAtomsStorage.class);
        mMmsStats = new MmsStats(mContext, mPersistMmsAtomsStorage, mSubId,
                mTelephonyManager, null, true, 0);
        mCarrierConfigValues = new Bundle();
        mCarrierConfigValues.putInt(
                CarrierConfigManager.KEY_MMS_MAX_NTN_PAYLOAD_SIZE_BYTES_INT,
                sMaxPduSize);
    }

    @After
    public void tearDown() {
        mContext = null;
        mTelephonyManager = null;
        mSubscriptionManager = null;
    }

    @Test
    public void sendRequest_noSatellite_sendSuccessful() {
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingUser= */ CALLING_USER,
                /* callingPkg= */ null, mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize + 100];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isTrue();
    }

    @Test
    public void sendRequest_connectedToSatellite_smallPdu_sendSuccessful() {
        ServiceState ss = new ServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setIsNonTerrestrialNetwork(true)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        doReturn(ss).when(mTelephonyManager).getServiceState();
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingUser= */ CALLING_USER,
                /* callingPkg= */ null, mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize - 1];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isTrue();
    }

    @Test
    public void sendRequest_connectedToSatellite_largePdu_sendSFails() {
        ServiceState ss = new ServiceState();
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setIsNonTerrestrialNetwork(true)
                .build();
        ss.addNetworkRegistrationInfo(nri);
        doReturn(ss).when(mTelephonyManager).getServiceState();
        SendRequest request = new SendRequest(mMmsService, mSubId, Uri.parse(sFakeUri),
                sFakeLocationUri, /* sentIntent= */ null, /* callingUser= */ CALLING_USER,
                /* callingPkg= */ null, mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = new byte[sMaxPduSize + 1];

        boolean okToSend = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToSend).isFalse();
    }

    @Test
    public void downloadRequest_noSatellite_downloadSuccessful() {
        doReturn(150L).when(mSmsManager).getWapMessageSize(sFakeUri);
        DownloadRequest request = new DownloadRequest(mMmsService, mSubId, sFakeUri,
                Uri.parse(sFakeUri), /* downloadIntent= */ null, CALLING_USER,
                /* callingPkg= */ null, mCarrierConfigValues, /* context= */ mMmsService,
                sFakeMessageId, mMmsStats, mTelephonyManager);

        boolean okToDownload = request.canTransferPayloadOnCurrentNetwork();

        assertThat(okToDownload).isTrue();
    }

    @Test
    public void testPersistIfRequired_caseVariantAuthority_doesNotOverwrite() throws Exception {
        // Setup mock PduPersister
        PduPersister mockPersister = mock(PduPersister.class);
        Field contextField = PduPersister.class.getDeclaredField("mContext");
        contextField.setAccessible(true);
        contextField.set(mockPersister, mContext);

        Field field = PduPersister.class.getDeclaredField("sPersister");
        field.setAccessible(true);
        field.set(null, mockPersister);

        // Grant WRITE_SMS permission to Context spy
        doReturn(PackageManager.PERMISSION_GRANTED)
                .when(mContext)
                .checkPermission(eq(android.Manifest.permission.WRITE_SMS), anyInt(), anyInt());

        // Prepare dummy pdu raw bytes representing a SendReq
        SendReq sendReq = new SendReq();
        PduComposer composer = new PduComposer(mContext, sendReq);
        byte[] pduData = composer.make();
        if (pduData == null) {
            pduData = new byte[]{
                (byte) 0x8C, (byte) 0x80, // X-Mms-Message-Type: m-send-req
                (byte) 0x98, (byte) 0x61,
                (byte) 0x62, (byte) 0x63, (byte) 0x00, // X-Mms-Transaction-Id: abc
                (byte) 0x8D, (byte) 0x90, // X-Mms-MMS-Version: 1.0
                (byte) 0x89, (byte) 0x14, // From
                (byte) 0x8A, (byte) 0x80 // To
            };
        }

        // We want to test case-variant authority "MMS" with ID 123
        Uri caseVariantUri = Uri.parse("content://MMS/123");

        SendRequest request = new SendRequest(mMmsService, mSubId, caseVariantUri,
                sFakeLocationUri, /* sentIntent= */ null, /* callingUser= */ CALLING_USER,
                /* callingPkg= */ "com.android.mms.service", mCarrierConfigValues, /* context= */ mContext,
                sFakeMessageId, mMmsStats, mTelephonyManager);
        request.mPduData = pduData;

        // Stub mockPersister.persist to return a dummy URI
        Uri persistedUri = Uri.parse("content://mms/sent/123");
        when(mockPersister.persist(any(GenericPdu.class), any(Uri.class), anyBoolean(), anyBoolean(), any()))
                .thenReturn(persistedUri);

        // Call persistIfRequired
        Uri resultUri = request.persistIfRequired(mContext, Activity.RESULT_OK, /* response= */ null);

        // Capture the insertUri passed to persister.persist
        ArgumentCaptor<Uri> uriCaptor = ArgumentCaptor.forClass(Uri.class);
        verify(mockPersister).persist(any(GenericPdu.class), uriCaptor.capture(), anyBoolean(), anyBoolean(), any());

        // Assert that the captured URI has authority "mms" and does NOT have ID appended.
        // If it was case-insensitive (broken), it would have matched "MMS", parsed ID 123, and appended it: "content://mms/sent/123".
        // With case-sensitive fix, it does NOT match "MMS", so it inserts to default: "content://mms/sent".
        Uri capturedUri = uriCaptor.getValue();
        assertThat(capturedUri.getAuthority()).isEqualTo("mms");
        assertThat(capturedUri.toString()).isEqualTo("content://mms/sent");
    }
}
