package aatwi.dev.cashcard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CashCardApplicationTests {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldReturnACashCardWhenDataIsSaved() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards/99", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        CashCard cashCard = parseCashCardsFrom(response.getBody());
        CashCard expected = new CashCard(99L, 123.45, "sarah1");
        assertEquals(expected, cashCard);
    }

    private CashCard parseCashCardsFrom(String responseBody) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(responseBody, CashCard.class);
    }

    private List<CashCard> parseCashCardsListFrom(String responseBody) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return Arrays.asList(mapper.readValue(responseBody, CashCard[].class));
    }

    @Test
    void shouldNotReturnACashCardWithAnUnknownId() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards/1000", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DirtiesContext
    void shouldCreateANewCashCard() throws JsonProcessingException {
        CashCard cashCard = new CashCard(null, 250.0, null);
        ResponseEntity<Void> createResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .postForEntity("/cashcards", cashCard, Void.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());

        URI locationOfNewCashCard = createResponse.getHeaders().getLocation();
        ResponseEntity<String> getResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity(locationOfNewCashCard, String.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        CashCard actual = parseCashCardsFrom(getResponse.getBody());

        assertNotNull(actual.id());
        assertEquals(250.0, actual.amount());
        assertEquals("sarah1", actual.owner());
    }

    @Test
    void shouldReturnAllCashCardsWhenListIsRequested() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<CashCard> actual = parseCashCardsListFrom(response.getBody());
        assertEquals(3, actual.size());

        List<CashCard> expected = Arrays.asList(
                new CashCard(99L, 123.45, "sarah1"),
                new CashCard(100L, 1.0, "sarah1"),
                new CashCard(101L, 150.0, "sarah1")
        );
        assertTrue(expected.containsAll(actual));
    }

    @Test
    void shouldReturnAPageOfCashCards() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards?page=0&size=1", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<CashCard> actual = parseCashCardsListFrom(response.getBody());
        assertEquals(1, actual.size());
    }

    @Test
    void shouldReturnASortedPageOfCashCards() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards?page=0&size=1&sort=amount,desc", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<CashCard> actual = parseCashCardsListFrom(response.getBody());
        assertEquals(1, actual.size());
        assertEquals(new CashCard(101L, 150.0, "sarah1"), actual.get(0));
    }

    @Test
    void shouldReturnASortedPageOfCashCardsWithNoParametersAndUseDefaultValues() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<CashCard> actual = parseCashCardsListFrom(response.getBody());
        assertEquals(3, actual.size());

        List<CashCard> expected = Arrays.asList(
                new CashCard(99L, 123.45, "sarah1"),
                new CashCard(100L, 1.0, "sarah1"),
                new CashCard(101L, 150.0, "sarah1")
        );
        assertTrue(expected.containsAll(actual));
    }

    @Test
    void shouldNotReturnACashCardWhenUsingBadCredentials() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("BAD_USER", "abc123")
                .getForEntity("/cashcards", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        response = restTemplate
                .withBasicAuth("sarah1", "BAD_PASSWORD")
                .getForEntity("/cashcards", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldRejectUsersWhoAreNotCardOwners() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("hank-owns-no-cards", "qrs456")
                .getForEntity("/cashcards/99", String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void shouldNotAllowAccessToCashCardsTheyDoNotOwn() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards/102", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DirtiesContext
    void shouldUpdateAnExistingCashCard() throws JsonProcessingException {
        CashCard cashCardUpdate = new CashCard(null, 19.99, null);
        HttpEntity<CashCard> request = new HttpEntity<>(cashCardUpdate);

        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/99", HttpMethod.PUT, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<String> getResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards/99", String.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        CashCard actual = this.parseCashCardsFrom(getResponse.getBody());
        CashCard expected = new CashCard(99L, 19.99, "sarah1");
        assertEquals(expected, actual);
    }

    @Test
    void itShouldNotUpdateACashCardThatDoesNotExist() {
        CashCard unKnownCashCard = new CashCard(null, 19.99, null);
        HttpEntity<CashCard> request = new HttpEntity<>(unKnownCashCard);
        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/99999", HttpMethod.PUT, request, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void itShouldNotUpdateACashCardThatIsOwnedBySomeoneElse() {
        CashCard unKnownCashCard = new CashCard(null, 333.33, null);
        HttpEntity<CashCard> request = new HttpEntity<>(unKnownCashCard);
        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/102", HttpMethod.PUT, request, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DirtiesContext
    void itShouldDeleteAnExistingCashCard() {
        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/99", HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<String> foundCashCardResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .getForEntity("/cashcards/99", String.class);

        assertEquals(HttpStatus.NOT_FOUND, foundCashCardResponse.getStatusCode());
    }

    @Test
    void itShouldNotDeleteACashCardThatDoesntExist() {
        ResponseEntity<Void> deleteResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/999999", HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, deleteResponse.getStatusCode());
    }

    @Test
    void shouldNotAllowDeletionOfCashCardsTheyDoNotOwn() {
        ResponseEntity<Void> deleteResponse = restTemplate
                .withBasicAuth("sarah1", "abc123")
                .exchange("/cashcards/102", HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, deleteResponse.getStatusCode());

        ResponseEntity<String> getResponse = restTemplate
                .withBasicAuth("kumar2", "xyz789")
                .getForEntity("/cashcards/102", String.class);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    }
}
