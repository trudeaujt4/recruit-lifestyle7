package com.mz.jarboot.core.utils;

import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
*
* @author majianzheng
*
*/
public class DateUtilsTest {

   @Test
   public void testGetCurrentDateWithCorrectFormat() {

       SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // supported date format
       Date date = new Date();
       Assert.assertEquals(DateUtils.formatDate(date), dateFormat.format(date).toString());

   }

   @Test
   public void testGetCurrentDateWithInCorrectFormat() {

       SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm"); // Not supported Date format
       Date date = new Date();
       Assert.assertNotEquals(DateUtils.formatDate(date), dateFormat.format(date).toString());

   }
}