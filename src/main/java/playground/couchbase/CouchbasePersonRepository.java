/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package playground.couchbase;

import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.document.EntityDocument;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.repository.AsyncRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import playground.Person;
import rx.Observable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * @author Sebastien Deleuze
 */
@Repository
public class CouchbasePersonRepository {

	private static final Logger logger = LoggerFactory.getLogger(CouchbasePersonRepository.class);

	private final AsyncRepository repository;
	private final AsyncBucket bucket;

	@Autowired
	public CouchbasePersonRepository(AsyncBucket bucket) {
		this.bucket = bucket;
		this.repository = bucket.repository().toBlocking().first();
	}

	public Observable<Void> insert(Observable<Person> personStream) {
		return personStream.flatMap(person -> {
			String id = person.getFirstname() + "_" + person.getLastname();
			EntityDocument doc = EntityDocument.create(id, person);
			return this.repository.insert(doc);
		}).flatMap(document -> Observable.empty());
	}

	public Observable<Person> list() {
		return this.bucket.query(N1qlQuery.simple("SELECT META(default).id FROM default"))
				.flatMap(result -> {
					if (result.parseSuccess()) {
						return result.rows().flatMap(row -> {
							String id = row.value().getString("id");
							return this.repository.get(id, Person.class).map(d -> d.content());
						});
					}
					else {
						return result.errors().flatMap(jsonErrors -> Observable.error(new IllegalStateException(jsonErrors.getInt("code") + ": " + jsonErrors.getString("msg"))));
					}
				});
	}

}
