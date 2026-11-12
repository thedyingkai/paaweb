package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;

class RatingRefreshListenerTests {
    @Test void committedChangesRefreshButRolledBackChangesDoNot() {
        try(var context=new AnnotationConfigApplicationContext()) {
            var ratings=mock(RatingService.class);
            context.getBeanFactory().registerSingleton("ratings",ratings);
            context.register(Config.class);context.refresh();
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status->{context.publishEvent(new RatingRefreshRequested());verifyNoInteractions(ratings);});
            verify(ratings,timeout(2000)).refreshAllRatings();clearInvocations(ratings);
            tx.executeWithoutResult(status->{context.publishEvent(new RatingRefreshRequested());status.setRollbackOnly();});
            verify(ratings,after(250).never()).refreshAllRatings();
            context.publishEvent(new RatingRefreshRequested());
            verify(ratings,timeout(2000)).refreshAllRatings();
        }
    }
    @Configuration @EnableAsync @EnableTransactionManagement static class Config {
        @Bean DataSource source(){return new DriverManagerDataSource("jdbc:h2:mem:refresh_events","sa","");}
        @Bean PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean RatingRefreshListener listener(RatingService ratings){return new RatingRefreshListener(ratings);}
    }
}
