package ai.medhaleak.ocrprocessor.integration;

import ai.medhaleak.ocrprocessor.model.OcrRecord;
import ai.medhaleak.ocrprocessor.model.User;
import ai.medhaleak.ocrprocessor.repository.OcrRecordRepository;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import ai.medhaleak.ocrprocessor.service.LlmService;
import ai.medhaleak.ocrprocessor.service.OcrEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OcrRecordRepositoryIT {

    @Autowired OcrRecordRepository ocrRecordRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean LlmService llmService;
    @MockBean OcrEngine ocrEngine;

    private User user;

    @BeforeEach
    void setUp() {
        ocrRecordRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("repouser_" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(user.getUsername() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user = userRepository.save(user);
    }

    @Test
    void searchByUser_emptyQuery_returnsAllRecords() {
        saveRecord("Invoice 2024", "invoice text content");
        saveRecord("Contract Draft", "agreement terms conditions");

        Page<OcrRecord> results = ocrRecordRepository.searchByUser(
                user.getId(), "", PageRequest.of(0, 20));

        assertThat(results.getTotalElements()).isEqualTo(2);
        Evidence.record("user=" + user.getUsername() + " q=\"\" records=Invoice 2024, Contract Draft",
                "totalElements=" + results.getTotalElements(),
                results.getContent().get(0).getDocumentName() + ", " + results.getContent().get(1).getDocumentName());
    }

    @Test
    void searchByUser_matchingQuery_returnsFilteredResults() {
        saveRecord("Invoice 2024", "total amount due payment invoice");
        saveRecord("Meeting Notes", "agenda action items discussed");

        Page<OcrRecord> results = ocrRecordRepository.searchByUser(
                user.getId(), "invoice", PageRequest.of(0, 20));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getDocumentName()).isEqualTo("Invoice 2024");
        Evidence.record("user=" + user.getUsername() + " q=\"invoice\"",
                "totalElements=" + results.getTotalElements(),
                "documentName=" + results.getContent().get(0).getDocumentName()
                        + " text=\"" + results.getContent().get(0).getExtractedText() + "\"");
    }

    @Test
    void searchByUser_noMatch_returnsEmptyPage() {
        saveRecord("Report", "quarterly earnings results");

        Page<OcrRecord> results = ocrRecordRepository.searchByUser(
                user.getId(), "xyznotfound", PageRequest.of(0, 20));

        assertThat(results.getTotalElements()).isEqualTo(0);
        Evidence.record("user=" + user.getUsername() + " q=\"xyznotfound\" stored text=\"quarterly earnings results\"",
                "totalElements=" + results.getTotalElements(), "content=[]");
    }

    @Test
    void searchByUser_scopedToOwner_doesNotReturnOtherUsersRecords() {
        // save record for this user
        saveRecord("Private Doc", "confidential information");

        // create another user and search
        User other = new User();
        other.setUsername("otherrepo_" + UUID.randomUUID().toString().substring(0, 8));
        other.setEmail(other.getUsername() + "@example.com");
        other.setPasswordHash(passwordEncoder.encode("pass"));
        other = userRepository.save(other);

        Page<OcrRecord> results = ocrRecordRepository.searchByUser(
                other.getId(), "", PageRequest.of(0, 20));

        assertThat(results.getTotalElements()).isEqualTo(0);
        Evidence.record("owner=" + user.getUsername() + " document=\"Private Doc\" searched as " + other.getUsername(),
                "totalElements=" + results.getTotalElements(), "content=[]");
    }

    @Test
    void findByIdAndUserId_wrongUser_returnsEmpty() {
        OcrRecord record = saveRecord("Doc", "text");
        UUID otherId = UUID.randomUUID();

        assertThat(ocrRecordRepository.findByIdAndUserId(record.getId(), otherId)).isEmpty();
        Evidence.record("recordId=" + record.getId() + " owner=" + user.getUsername() + " lookupUser=" + otherId,
                "empty", "Optional.empty");
    }

    @Test
    void searchByUser_pagination_worksCorrectly() {
        for (int i = 0; i < 5; i++) {
            saveRecord("Doc " + i, "document text content number " + i);
        }

        Page<OcrRecord> page0 = ocrRecordRepository.searchByUser(
                user.getId(), "", PageRequest.of(0, 2));
        Page<OcrRecord> page1 = ocrRecordRepository.searchByUser(
                user.getId(), "", PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page0.getContent().get(0).getId())
                .isNotEqualTo(page1.getContent().get(0).getId());
        Evidence.record("user=" + user.getUsername() + " 5 records q=\"\" pageSize=2",
                "totalElements=" + page0.getTotalElements(),
                "page0=" + page0.getContent().stream().map(OcrRecord::getDocumentName).toList()
                        + " page1=" + page1.getContent().stream().map(OcrRecord::getDocumentName).toList());
    }

    private OcrRecord saveRecord(String name, String text) {
        OcrRecord r = new OcrRecord();
        r.setUser(user);
        r.setDocumentName(name);
        r.setOriginalFilename(name + ".png");
        r.setFileType("image/png");
        r.setFileSizeBytes(1024);
        r.setExtractedText(text);
        r.setStatus(OcrRecord.Status.COMPLETE);
        r.setSummaryStatus(OcrRecord.SummaryStatus.SKIPPED);
        return ocrRecordRepository.save(r);
    }
}
