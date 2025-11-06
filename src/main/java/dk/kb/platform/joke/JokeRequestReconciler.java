/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package dk.kb.platform.joke;

import dk.kb.platform.joke.JokeRequestSpec.ExcludedTopic;
import dk.kb.platform.joke.JokeRequestStatus.State;
import dk.kb.platform.joke.jokerestclient.JokeModel;
import dk.kb.platform.joke.jokerestclient.JokeService;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata.Icon;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CSVMetadata(bundleName = "joke-operator",
             requiredCRDs = @CSVMetadata.RequiredCRD(kind = "Joke",
                                                     name = Joke.NAME,
                                                     version = Joke.VERSION),
             icon = @Icon(fileName = "icon.png", mediatype = "image/png"))

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE))

@RBACRule(apiGroups = Joke.GROUP, resources = "jokes", verbs = RBACRule.ALL)

@SuppressWarnings("unused")
public class JokeRequestReconciler implements Reconciler<JokeRequest> {
    
    @Inject
    Logger logger;
    
    @Inject
    @RestClient
    JokeService jokeRestService;
    
    @Inject
    KubernetesClient k8s;
    
    @Override
    public UpdateControl<JokeRequest> reconcile(JokeRequest jokeRequest, Context<JokeRequest> context) {
        //TODO get duration from controller config
        return reconcile(jokeRequest).rescheduleAfter(Duration.of(10, ChronoUnit.SECONDS));
        
    }
    
    private @NotNull UpdateControl<JokeRequest> reconcile(JokeRequest jokeRequest) {
        final var spec = jokeRequest.getSpec();
        {
            JokeRequestStatus status = jokeRequest.getStatus();
            logger.infov("Starting reconcile with request {0}", status);
            if (status != null && State.CREATED == status.state() && Objects.equals(spec, status.appliedSpec)) {
                if (status.jokeId() != null) {
                    if (k8s.resources(Joke.class)
                           .withName(status.jokeId())
                           .get() != null) {
                        logger.infov("Determined no update for status {0}", status);
                        return UpdateControl.noUpdate();
                    }
                }
            }
        }
        
        var newStatus = reconcileJoke(jokeRequest, spec);
        jokeRequest.setStatus(newStatus);
        return UpdateControl.patchStatus(jokeRequest);
    }
    
    private JokeRequestStatus reconcileJoke(JokeRequest jokeRequest, JokeRequestSpec spec) {
        JokeRequestStatus status;
        try {
            final JokeModel restResponse = getJokeModel(spec);
            
            status = JokeRequestStatus.from(restResponse).appliedSpec(spec);
            
            if (!restResponse.error()) {
                final var newJoke = Joke.from(restResponse);
                
                OwnerReference ownerReference = createOwnerReference(jokeRequest);
                newJoke.addOwnerReference(ownerReference);
                
                deleteManagedJokes(ownerReference, jokeRequest.getMetadata().getNamespace());
                
                k8s.resource(newJoke).create();
                status.message("Joke " + newJoke.id() + " created")
                      .jokeId(newJoke.getMetadata().getName())
                      .state(State.CREATED);
            } else {
                status.message("Failed with " + restResponse).error(true).state(State.ERROR);
            }
        } catch (Exception e) {
            logger.errorv(e, "Failed");
            status = new JokeRequestStatus()
                .message("Error querying API: " + e.getMessage())
                .state(State.ERROR)
                .error(true);
        }
        return status;
    }
    
    private void deleteManagedJokes(OwnerReference ownerReference, String namespace) {
        final var k8sResources = k8s.resources(Joke.class);
        k8sResources.inNamespace(namespace)
                    .resources()
                    .filter(jokeResource -> jokeResource.item()
                                                        .getMetadata()
                                                        .getOwnerReferences()
                                                        .contains(ownerReference))
                    .forEach(jokeResource -> jokeResource.withTimeout(10, TimeUnit.SECONDS).delete());
    }
    
    private static @NotNull OwnerReference createOwnerReference(JokeRequest jokeRequest) {
        OwnerReference ownerReference = new OwnerReference(jokeRequest.getApiVersion(),
                                                           true,
                                                           true,
                                                           jokeRequest.getCRDName(),
                                                           jokeRequest.getMetadata().getName(),
                                                           jokeRequest.getMetadata().getUid());
        return ownerReference;
    }
    
    private JokeModel getJokeModel(JokeRequestSpec spec) {
        logger.infov("starting rest request with ''{0}'',''{1}'',''{2}'',''{3}''",
                     spec.category(),
                     excludedAsString(spec.excluded()),
                     spec.safe(),
                     "single");
        final JokeModel restResponse = jokeRestService.getRandom(spec.category(),
                                                                 excludedAsString(spec.excluded()),
                                                                 spec.safe(),
                                                                 "single");
        logger.infov("received rest response {0}", restResponse);
        return restResponse;
    }
    
    private static <K, V> Map<String, String> toMapStringString(Map<K, V> mapStringBool, String keyPrefix) {
        return mapStringBool
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                entry -> keyPrefix + entry.getKey().toString(),
                entry -> entry.getValue().toString()));
    }
    
    private static String excludedAsString(Collection<ExcludedTopic> excluded) {
        return excluded.stream()
                       .map(ExcludedTopic::name)
                       .collect(Collectors.joining(","));
    }
}