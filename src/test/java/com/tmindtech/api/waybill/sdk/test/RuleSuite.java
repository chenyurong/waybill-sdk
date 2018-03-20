package com.tmindtech.api.waybill.sdk.test;

import org.apache.log4j.BasicConfigurator;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * 单元测试入口
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        StatusTest.class
})
public class RuleSuite {
    @ClassRule
    public static ExternalResource externalResource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            //初始化 slf4j
            BasicConfigurator.configure();
        }
    };

}
