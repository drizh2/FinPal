package com.dadry.finpal.repository;

import com.dadry.finpal.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByGroup_IdOrderByNameAsc(Long groupId);

    List<GroupMember> findByUserId(Long userId);

    boolean existsByGroup_IdAndUserId(Long groupId, Long userId);

    Optional<GroupMember> findByGroup_IdAndUserId(Long groupId, Long userId);

    long countByGroup_Id(Long groupId);
}
