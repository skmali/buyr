package com.malison.catalogui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "eureka.client.enabled=false"
})
class CatalogUiApplicationTests {

    @Test
    void contextLoads() {
    }

}
