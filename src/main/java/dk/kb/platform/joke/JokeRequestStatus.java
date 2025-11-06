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


import dk.kb.platform.joke.jokerestclient.JokeModel;
import lombok.Data;

@Data
public class JokeRequestStatus {
    public enum State {
        CREATED,
        ALREADY_PRESENT,
        PROCESSING,
        ERROR,
        UNKNOWN
    }

    public State state = State.UNKNOWN;
    public boolean error;
    public String message;
    public JokeRequestSpec appliedSpec;
    public String jokeId;
    
    public static JokeRequestStatus from(JokeModel model) {
        final var status = new JokeRequestStatus();
        if (model.error()) {
            status.error = true;
            status.message = model.message() + ": " + model.additionalInfo();
            status.state = State.ERROR;
        } else {
            status.error = false;
            status.state = State.PROCESSING;
        }
        return status;
    }
    
}
