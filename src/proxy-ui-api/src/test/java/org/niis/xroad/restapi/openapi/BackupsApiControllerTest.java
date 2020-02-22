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

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Backup;
import org.niis.xroad.restapi.repository.BackupsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test BackupsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class BackupsApiControllerTest {

    @MockBean
    BackupsRepository backupsRepository;

    @Autowired
    private BackupsApiController backupsApiController;

    private static final String BASE_DIR = "/tmp/backups/";

    private static final String BACKUP_FILE_1_NAME = "ss-automatic-backup-2020_02_19_031502.tar";

    private static final String BACKUP_FILE_1_CREATED_AT = "2020-02-19T03:15:02.451Z";

    private static final Long BACKUP_FILE_1_CREATED_AT_MILLIS = 1582082102451L;

    private static final String BACKUP_FILE_2_NAME = "ss-automatic-backup-2020_02_12_031502.tar";

    private static final String BACKUP_FILE_2_CREATED_AT = "2020-02-12T03:15:02.684Z";

    private static final Long BACKUP_FILE_2_CREATED_AT_MILLIS = 1581477302684L;

    @Test
    @WithMockUser(authorities = { "BACKUP_CONFIGURATION" })
    public void getBackups() throws Exception {
        List<File> files = new ArrayList<>(Arrays.asList(new File(BASE_DIR + BACKUP_FILE_1_NAME),
                new File(BASE_DIR + BACKUP_FILE_2_NAME)));

        when(backupsRepository.getBackupFiles()).thenReturn(files);
        when(backupsRepository.getCreatedAt(BACKUP_FILE_1_NAME)).thenReturn(new Date(BACKUP_FILE_1_CREATED_AT_MILLIS));
        when(backupsRepository.getCreatedAt(BACKUP_FILE_2_NAME)).thenReturn(new Date(BACKUP_FILE_2_CREATED_AT_MILLIS));

        ResponseEntity<List<Backup>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<Backup> backups = response.getBody();
        assertEquals(files.size(), backups.size());
        assertEquals(BACKUP_FILE_1_NAME, backups.get(0).getFilename());
        assertEquals(BACKUP_FILE_1_CREATED_AT, backups.get(0).getCreatedAt().toString());
        assertEquals(BACKUP_FILE_2_NAME, backups.get(1).getFilename());
        assertEquals(BACKUP_FILE_2_CREATED_AT, backups.get(1).getCreatedAt().toString());
    }

    @Test
    @WithMockUser(authorities = { "BACKUP_CONFIGURATION" })
    public void getBackupsEmptyList() {
        List<File> files = new ArrayList<>();
        when(backupsRepository.getBackupFiles()).thenReturn(files);

        ResponseEntity<List<Backup>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<Backup> backups = response.getBody();
        assertEquals(files.size(), backups.size());
    }

    @Test
    @WithMockUser(authorities = { "BACKUP_CONFIGURATION" })
    public void getBackupsException() {
        when(backupsRepository.getBackupFiles()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<List<Backup>> response = backupsApiController.getBackups();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }
}