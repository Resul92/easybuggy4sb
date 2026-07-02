package org.t246osslab.easybuggy4sb.troubles;

import static org.junit.Assert.assertFalse;

import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletContext;

import org.junit.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

public class ThreadStarvationControllerTest {

    @Test
    public void lockIsReleasedWhenHistoryProcessingFails() throws Exception {
        ThreadStarvationController controller = new ThreadStarvationController();
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("msg.unknown.exception.occur", Locale.ENGLISH, "error");
        ReflectionTestUtils.setField(controller, "msg", messageSource);

        try {
            controller.process(new MissingTempDirRequest(), new ModelAndView(), Locale.ENGLISH);

            assertFalse("The controller lock must not remain held after an exception",
                    ((ReentrantLock) controller.lock).isLocked());
        } finally {
            ReentrantLock lock = (ReentrantLock) controller.lock;
            while (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static class MissingTempDirRequest extends MockHttpServletRequest {
        private final ServletContext servletContext = new MockServletContext();

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }
    }
}
