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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import dk.kb.platform.joke.jokerestclient.JokeModel;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

@Group(Joke.GROUP)
@Version(Joke.VERSION)
@JsonInclude(Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Joke extends CustomResource<Void, Void> implements Namespaced {

    public static final String GROUP = "joke.platform.kb.dk";
    public static final String VERSION = "v1alpha1";
    public static final String NAME = "jokes." + GROUP;
    
    public String joke;
    public String category;
    public boolean safe;
    public String lang;
    public String id;

    public Joke(String id, String joke, String category, boolean safe, String lang) {
        this.id = id;
        getMetadata().setName(id);
        this.joke = joke;
        this.category = category;
        this.safe = safe;
        this.lang = lang;
    }
    
    public static Joke from(JokeModel jokeModel){
        return new Joke(jokeModel.id()+"",  jokeModel.joke(), jokeModel.category(), jokeModel.safe(), jokeModel.lang());
    }
}
