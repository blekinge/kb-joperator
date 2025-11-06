package dk.kb.platform.joke;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import dk.kb.platform.joke.jokerestclient.JokeModel;
import dk.kb.platform.joke.jokerestclient.JokeService;

import java.util.Map;

import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import dk.kb.platform.joke.JokeRequestSpec.Category;
import dk.kb.platform.joke.JokeRequestStatus.State;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class JokeRequestReconcilerTest {
    
    @Inject
    @InjectMock
    @RestClient
    JokeService jokeService;
    
    @Inject
    KubernetesClient client;
    
    @Test
    void canReconcile() {
        // arrange
        final JokeModel joke = JokeModel.builder()
                                        .id(1)
                                        .joke("Hello")
                                        .flags(Map.of())
                                        .build();
        
        Mockito.when(jokeService.getRandom(eq(Category.Any), anyString(), anyBoolean(), anyString())).thenReturn(joke);
        
        final JokeRequest testRequest = new JokeRequest();
        testRequest.setMetadata(new ObjectMetaBuilder()
                                    .withName("myjoke1")
                                    .withNamespace(client.getNamespace())
                                    .build());
        testRequest.setSpec(new JokeRequestSpec());
        testRequest.getSpec().category(Category.Any);
        
        // act
        client.resource(testRequest).create();
        
        // assert
        await().ignoreException(NullPointerException.class).atMost(300, SECONDS).untilAsserted(() -> {
            JokeRequest updatedRequest = client.resources(JokeRequest.class)
                                               .inNamespace(testRequest.getMetadata().getNamespace())
                                               .withName(testRequest.getMetadata().getName())
                                               .get();
            assertThat(updatedRequest.getStatus(), is(notNullValue()));
            assertThat(updatedRequest.getStatus().state(), equalTo(State.CREATED));
        });
        
        var createdJokes = client.resources(Joke.class)
                                 .inNamespace(testRequest.getMetadata().getNamespace())
                                 .list();
        
        assertThat(createdJokes.getItems(), is(not(empty())));
        assertThat(createdJokes.getItems().getFirst().joke(), is("Hello"));
    }
    
}
