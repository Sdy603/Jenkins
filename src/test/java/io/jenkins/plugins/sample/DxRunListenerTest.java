package io.jenkins.plugins.sample;

import hudson.model.Result;
import org.junit.Test;
import static org.junit.Assert.*;

/** Basic tests for DxRunListener. */
public class DxRunListenerTest {

    @Test
    public void testResultMapping() {
        assertEquals("success", DxRunListener.mapResult(Result.SUCCESS));
        assertEquals("failed", DxRunListener.mapResult(Result.FAILURE));
        assertEquals("aborted", DxRunListener.mapResult(Result.ABORTED));
    }
}

