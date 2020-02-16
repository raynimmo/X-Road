/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.TspType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.ServerConfService;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test TimestampingServiceApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class TimestampingServiceApiControllerTest {

    @MockBean
    GlobalConfService globalConfService;

    @MockBean
    ServerConfService serverConfService;

    @MockBean
    GlobalConfFacade globalConfFacade;

    @Autowired
    private TimestampingServicesApiController timestampingServicesApiController;

    private static final Map<String, String> APPROVED_TIMESTAMPING_SERVICES = new HashMap<>();

    private static final List<TspType> CONFIGURED_TIMESTAMPING_SERVICES = new ArrayList<>();

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    private static final boolean SHOW_CONFIGURED_FALSE = false;

    private static final boolean SHOW_CONFIGURED_TRUE = true;

    @Before
    public void setup() {

        APPROVED_TIMESTAMPING_SERVICES.put(TSA_1_URL, TSA_1_NAME);
        APPROVED_TIMESTAMPING_SERVICES.put(TSA_2_URL, TSA_2_NAME);

        CONFIGURED_TIMESTAMPING_SERVICES.addAll(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME)));

        when(globalConfFacade.getInstanceIdentifier()).thenReturn("TEST");
        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(
                new ArrayList<String>(APPROVED_TIMESTAMPING_SERVICES.keySet()));
        when(globalConfService.getApprovedTspName(TSA_1_URL))
                .thenReturn(APPROVED_TIMESTAMPING_SERVICES.get(TSA_1_URL));
        when(globalConfService.getApprovedTspName(TSA_2_URL))
                .thenReturn(APPROVED_TIMESTAMPING_SERVICES.get(TSA_2_URL));
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(CONFIGURED_TIMESTAMPING_SERVICES);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getApprovedTimestampingServices() {
        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices(SHOW_CONFIGURED_FALSE);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(APPROVED_TIMESTAMPING_SERVICES.keySet().size(), timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getApprovedTimestampingServicesEmptyList() {
        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(new ArrayList<String>());
        when(globalConfService.getApprovedTspName(any())).thenReturn(null);

        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices(SHOW_CONFIGURED_FALSE);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServices() {
        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices(SHOW_CONFIGURED_TRUE);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(CONFIGURED_TIMESTAMPING_SERVICES.size(), timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getConfiguredTimestampingServicesEmptyList() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices(SHOW_CONFIGURED_TRUE);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        ResponseEntity<TimestampingService> response = timestampingServicesApiController
                .addTimestampingService(timestampingService);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(2, serverConfService.getConfiguredTimestampingServices().size());
        assertEquals(serverConfService.getConfiguredTimestampingServices().get(1).getName(),
                timestampingService.getName());
        assertEquals(serverConfService.getConfiguredTimestampingServices().get(1).getUrl(),
                timestampingService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addDuplicateConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        try {
            ResponseEntity<TimestampingService> response = timestampingServicesApiController
                    .addTimestampingService(timestampingService);
            fail("should throw ConflictException");
        } catch (ConflictException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_TSP" })
    public void addNonExistingConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));
        TimestampingService timestampingService = TestUtils
                .createTimestampingService("http://dummy.com", "Dummy");

        try {
            ResponseEntity<TimestampingService> response = timestampingServicesApiController
                    .addTimestampingService(timestampingService);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(
                new ArrayList<TspType>(Arrays.asList(TestUtils.createTspType(TSA_1_URL, TSA_1_NAME))));

        ResponseEntity<Void> response = timestampingServicesApiController
                .deleteTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, serverConfService.getConfiguredTimestampingServices().size());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_TSP" })
    public void deleteNonExistingConfiguredTimestampingService() {
        when(serverConfService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<TspType>());

        try {
            ResponseEntity<Void> response = timestampingServicesApiController
                    .deleteTimestampingService(TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME));
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // success
        }
    }
}