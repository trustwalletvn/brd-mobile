package com.breadwallet.database;

import android.app.Activity;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;
import com.breadwallet.legacy.presenter.activities.settings.TestActivity;
import com.breadwallet.legacy.presenter.entities.CurrencyEntity;
import com.breadwallet.tools.sqlite.RatesDataSource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;


/**
 * BreadWallet
 *
 * Created by Mihail Gutan <mihail@breadwallet.com> on 9/30/16.
 * Copyright (c) 2021 Breadwinner AG
 *
 * SPDX-License-Identifier: BUSL-1.1
 */

@RunWith(AndroidJUnit4.class)
@LargeTest
public class DatabaseTests {
    public static final String TAG = DatabaseTests.class.getName();
    final CountDownLatch signal = new CountDownLatch(1000);



    @Rule
    public ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setUp() {
        Log.e(TAG, "setUp: ");
//        Activity app = mActivityRule.getActivity();
//        BRCoreMasterPubKey pubKey = new BRCoreMasterPubKey("cat circle quick rotate arena primary walnut mask record smile violin state".getBytes(), true);
//        BRKeyStore.putMasterPublicKey(pubKey.serialize(), app);
        cleanUp();
    }

    private void cleanUp() {
        Activity app = mActivityRule.getActivity();

    }

    @After
    public void tearDown() {

    }

    @Test
    @Ignore
    public void testSetLocal() {
        Activity app = mActivityRule.getActivity();

        // Test inserting OMG as a currency
        RatesDataSource cds = RatesDataSource.getInstance(mActivityRule.getActivity());
        List<CurrencyEntity> toInsert = new ArrayList<>();
        CurrencyEntity ent = new CurrencyEntity(
 "OMG",
        "OmiseGo",
        8.43f,
         "BTC"
        );
        toInsert.add(ent);
        cds.putCurrencies(toInsert);
        List<CurrencyEntity> cs = cds.getAllCurrencies("BTC");
        Assert.assertNotNull(cs);
        Assert.assertEquals(cs.size(), 1);
        Assert.assertEquals(cs.get(0).getName(), "OmiseGo");
        Assert.assertEquals(cs.get(0).getCode(), "OMG");
        Assert.assertEquals(cs.get(0).getRate(), 8.43f, 0);

        // Test inserting BTC as a currency
        toInsert = new ArrayList<>();

        CurrencyEntity btcEntity = new CurrencyEntity(
                "ETH",
                "Ether",
                6f,
                "BCH"
        );
        toInsert.add(btcEntity);
        cds.putCurrencies(toInsert);

        List<CurrencyEntity> btcCurs = cds.getAllCurrencies("BTC");
        List<CurrencyEntity> bchCurs = cds.getAllCurrencies("BCH");
        Assert.assertNotNull(btcCurs);
        Assert.assertNotNull(bchCurs);
        Assert.assertEquals(btcCurs.size(), 1);
        Assert.assertEquals(bchCurs.size(), 1);
        Assert.assertEquals(bchCurs.get(0).getName(), "Ether");
        Assert.assertEquals(bchCurs.get(0).getCode(), "ETH");
        Assert.assertEquals(bchCurs.get(0).getRate(), 6f, 0);

        Assert.assertEquals(btcCurs.get(0).getName(), "OmiseGo");
        Assert.assertEquals(btcCurs.get(0).getCode(), "OMG");
        Assert.assertEquals(btcCurs.get(0).getRate(), 8.43f, 0);
    }

    private synchronized void done() {
        signal.countDown();
    }
}
