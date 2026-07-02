package org.t246osslab.easybuggy4sb.troubles;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

public class WeakReferenceControllerTest {

    private final Logger logger = Logger.getLogger(WeakReferenceController.class.getCanonicalName());

    @Before
    public void setUp() {
        removeHandlers();
    }

    @After
    public void tearDown() {
        removeHandlers();
    }

    @Test
    public void changingLogLevelDoesNotAccumulateHandlers() throws IOException {
        WeakReferenceController controller = new WeakReferenceController();

        controller.process("INFO", new RedirectAttributesModelMap());
        controller.process("WARNING", new RedirectAttributesModelMap());

        assertEquals(1, logger.getHandlers().length);
    }

    private void removeHandlers() {
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
