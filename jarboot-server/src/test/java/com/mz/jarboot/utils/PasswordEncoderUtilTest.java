package com.mz.jarboot.utils;

import org.junit.Assert;
import org.junit.Test;

public class PasswordEncoderUtilTest {
    @Test
    public void test() {
        String encoded = PasswordEncoderUtil.encode("123");
        Assert.assertTrue(PasswordEncoderUtil.matches("123", encoded));

        encoded = PasswordEncoderUtil.encode("abc123");
        Assert.assertTrue(PasswordEncoderUtil.matches("abc123", encoded));
    }
}
