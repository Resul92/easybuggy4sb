package org.t246osslab.easybuggy4sb.controller;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.t246osslab.easybuggy4sb.core.model.User;

public class DefaultLoginControllerTest {

    @Before
    public void setUp() throws Exception {
        replaceLoginHistory(new ConcurrentHashMap<String, User>());
    }

    @After
    public void tearDown() throws Exception {
        replaceLoginHistory(new ConcurrentHashMap<String, User>());
    }

    @Test
    public void expiredFailedLoginEntriesAreRemovedFromHistory() throws Exception {
        DefaultLoginController controller = new DefaultLoginController();
        controller.accountLockCount = 1;
        controller.accountLockTime = 1;

        controller.incrementLoginFailedCount("stale-user");
        ConcurrentHashMap<String, User> history = loginHistory();
        history.get("stale-user").setLastLoginFailedTime(new Date(System.currentTimeMillis() - 1000));

        assertFalse(controller.isAccountLocked("stale-user"));
        assertFalse("Expired lock records should not remain in the static login history",
                history.containsKey("stale-user"));
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, User> loginHistory() throws Exception {
        Field field = DefaultLoginController.class.getDeclaredField("userLoginHistory");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, User>) field.get(null);
    }

    private static void replaceLoginHistory(ConcurrentHashMap<String, User> history) throws Exception {
        Field field = DefaultLoginController.class.getDeclaredField("userLoginHistory");
        field.setAccessible(true);
        field.set(null, history);
    }
}
