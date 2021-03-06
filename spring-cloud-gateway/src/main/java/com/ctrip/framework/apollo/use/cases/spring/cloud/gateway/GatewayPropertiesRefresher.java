package com.ctrip.framework.apollo.use.cases.spring.cloud.gateway;

import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * @author ksewen
 * @date 2019/5/175:24 PM
 */
@Component
public class GatewayPropertiesRefresher implements ApplicationContextAware,ApplicationEventPublisherAware {

    private static final Logger logger = LoggerFactory.getLogger(GatewayPropertiesRefresher.class);

    private static final String ID_PATTERN = "spring\\.cloud\\.gateway\\.routes\\[\\d+\\]\\.id";

    private static final String DEFAULT_FILTER_PATTERN = "spring\\.cloud\\.gateway\\.default-filters\\[\\d+\\]\\.name";

    private ApplicationContext applicationContext;

    private ApplicationEventPublisher publisher;

    @Autowired
    private GatewayProperties gatewayProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    @ApolloConfigChangeListener(interestedKeyPrefixes = "spring.cloud.gateway.")
    public void onChange(ConfigChangeEvent changeEvent) {
        refreshGatewayProperties(changeEvent);
    }

    /***
     * ??????org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator????????????routes
     *
     * @param changeEvent
     * @return void
     * @author ksewen
     * @date 2019/5/21 2:13 PM
     */
    private void refreshGatewayProperties(ConfigChangeEvent changeEvent) {
        logger.info("Refreshing GatewayProperties!");
        preDestroyGatewayProperties(changeEvent);
        this.applicationContext.publishEvent(new EnvironmentChangeEvent(changeEvent.changedKeys()));
        refreshGatewayRouteDefinition();
        logger.info("GatewayProperties refreshed!");
    }

    /***
     * GatewayProperties??????@PreDestroy???destroy??????
     * org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder#rebind(java.lang.String)???destroyBean???????????????????????????
     * ?????????spring.cloud.gateway.??????????????????????????????????????????????????????????????????????????????????????????initializeBean????????????????????????bean??????return??????bean
     * ???????????????spring.cloud.gateway.routes[n]???spring.cloud.gateway.default-filters[n]????????????initializeBean???????????????????????????????????????bean
     * ???????????????????????????@PreDestroy????????????????????????????????????????????????org.springframework.cloud.gateway.config.GatewayProperties#routes
     * ???org.springframework.cloud.gateway.config.GatewayProperties#defaultFilters??????????????????
     *
     * @param
     * @return void
     * @author ksewen
     * @date 2019/5/21 2:13 PM
     */
    private synchronized void preDestroyGatewayProperties(ConfigChangeEvent changeEvent) {
        logger.info("Pre Destroy GatewayProperties!");
        final boolean needClearRoutes = this.checkNeedClear(changeEvent, ID_PATTERN, this.gatewayProperties.getRoutes().size());
        if (needClearRoutes) {
            this.gatewayProperties.setRoutes(new ArrayList<>());
        }
        final boolean needClearDefaultFilters = this.checkNeedClear(changeEvent, DEFAULT_FILTER_PATTERN, this.gatewayProperties.getDefaultFilters().size());
        if (needClearDefaultFilters) {
            this.gatewayProperties.setDefaultFilters(new ArrayList<>());
        }
        logger.info("Pre Destroy GatewayProperties finished!");
    }

    private void refreshGatewayRouteDefinition() {
        logger.info("Refreshing Gateway RouteDefinition!");
        this.publisher.publishEvent(new RefreshRoutesEvent(this));
        logger.info("Gateway RouteDefinition refreshed!");
    }

    /***
     * ??????changeEvent????????????pattern??????key?????????????????????PropertyChangeType???DELETED???????????????GatewayProperties???????????????
     *
     * @param changeEvent
     * @param pattern
     * @param existSize
     * @return boolean
     * @author ksewen
     * @date 2019/5/23 2:18 PM
     */
    private boolean checkNeedClear(ConfigChangeEvent changeEvent, String pattern, int existSize) {

        return changeEvent.changedKeys().stream().filter(key -> key.matches(pattern))
                .filter(key -> {
                    ConfigChange change = changeEvent.getChange(key);
                    return PropertyChangeType.DELETED.equals(change.getChangeType());
                }).count() == existSize;
    }

}
