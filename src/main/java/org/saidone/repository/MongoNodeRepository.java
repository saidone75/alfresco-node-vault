package org.saidone.repository;

import org.saidone.model.NodeWrapper;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoNodeRepository extends MongoRepository<NodeWrapper, String> {}