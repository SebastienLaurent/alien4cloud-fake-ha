package org.alien4cloud.bootstrap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import alien4cloud.events.HALeaderElectionEvent;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class HAManager implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

    @Value("${ha.ha_enabled:#{false}}")
    private boolean haEnabled;

    private boolean master = false;

    @Resource
    private ApplicationContext alienContext;

    private ScheduledThreadPoolExecutor scheduler;

    private Runnable checkTask = new Runnable() {
        @Override
        public void run() {
            boolean hafile = checkFile();

            if (hafile==false && master == false) {
                elect();
            } else if (hafile==true && master==true) {
                banish();
            }
        }
    };

    @PostConstruct
    public void init() {
        if (!haEnabled) {
            log.info("HA is not enabled, H.A. Manager does not try to manage state.");
            return;
        }

        scheduler = new ScheduledThreadPoolExecutor(1);
    }

    private void elect() {
        master = true;
        alienContext.publishEvent(new HALeaderElectionEvent(this, master));
    }

    private void banish() {
        master = false;
        alienContext.publishEvent(new HALeaderElectionEvent(this, master));
    }

    /**
     * Entry point. begin the process once the container is up
     */
    @Override
    public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
        log.info("EmbeddedServletContainer is ready");
        scheduler.scheduleWithFixedDelay(checkTask,0,10, TimeUnit.SECONDS);
    }

    private boolean checkFile() {
        return Files.exists(Paths.get("slave"));
    }
}
