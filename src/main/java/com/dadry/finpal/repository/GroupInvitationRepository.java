package com.dadry.finpal.repository;

import com.dadry.finpal.model.GroupInvitation;
import com.dadry.finpal.model.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    Optional<GroupInvitation> findByGroup_IdAndStatus(Long groupId, InvitationStatus status);

    boolean existsByGroup_IdAndInvitedUserIdAndStatus(Long groupId, Long invitedUserId, InvitationStatus status);

    List<GroupInvitation> findByInvitedUserIdAndStatusOrderByCreatedAtDesc(Long invitedUserId, InvitationStatus status);

    Optional<GroupInvitation> findByIdAndInvitedUserIdAndStatus(Long id, Long invitedUserId, InvitationStatus status);
}
