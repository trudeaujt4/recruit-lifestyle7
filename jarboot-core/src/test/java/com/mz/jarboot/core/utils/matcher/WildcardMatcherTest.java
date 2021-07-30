package com.mz.jarboot.core.utils.matcher;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author majianzheng
 */
public class WildcardMatcherTest {

    @Test
    public void testMatching(){
        Assert.assertFalse(new WildcardMatcher(null).matching(null));
        Assert.assertFalse(new WildcardMatcher(null).matching("foo"));
        Assert.assertFalse(new WildcardMatcher("foo").matching(null));

        Assert.assertTrue(new WildcardMatcher("foo").matching("foo"));
        Assert.assertFalse(new WildcardMatcher("foo").matching("bar"));

        Assert.assertTrue(new WildcardMatcher("foo*").matching("foo"));
        Assert.assertTrue(new WildcardMatcher("foo*").matching("fooooooobar"));
        Assert.assertTrue(new WildcardMatcher("f*r").matching("fooooooobar"));
        Assert.assertFalse(new WildcardMatcher("foo*").matching("fo"));
        Assert.assertFalse(new WildcardMatcher("foo*").matching("bar"));

        Assert.assertFalse(new WildcardMatcher("foo?").matching("foo"));
        Assert.assertTrue(new WildcardMatcher("foo?").matching("foob"));

        Assert.assertTrue(new WildcardMatcher("foo\\*").matching("foo*"));
        Assert.assertFalse(new WildcardMatcher("foo\\*").matching("foooooo"));

        Assert.assertTrue(new WildcardMatcher("foo\\?").matching("foo?"));
        Assert.assertFalse(new WildcardMatcher("foo\\?").matching("foob"));
    }

}
