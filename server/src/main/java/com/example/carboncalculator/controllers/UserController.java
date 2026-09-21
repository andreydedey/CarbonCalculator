package com.example.carboncalculator.controllers;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.ChangeRoleRequest;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.services.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<UserMemberDTO> list(Pageable pageable) {
        return PageResponse.from(userService.listMembers(pageable));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserMemberDTO> invite(@RequestBody InviteRequest request,
                                                 @AuthenticationPrincipal AppUser user) {
        UserMemberDTO member = userService.invite(request.email(), request.role(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('MANAGER')")
    public UserMemberDTO changeRole(@PathVariable UUID id,
                                     @RequestBody ChangeRoleRequest request,
                                     @AuthenticationPrincipal AppUser user) {
        return userService.changeRole(id, request.role(), user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUser user) {
        userService.revoke(id, user);
        return ResponseEntity.noContent().build();
    }

}
