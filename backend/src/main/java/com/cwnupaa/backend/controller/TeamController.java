package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.TeamVO;
import com.cwnupaa.backend.vo.TeamMemberVO;
import com.cwnupaa.backend.vo.UserSearchVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team")
public class TeamController {
    @Autowired private org.springframework.context.ApplicationEventPublisher ratingEvents;

    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private TeamMemberMapper teamMemberMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private EmailService emailService;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @PostMapping("/create")
    @Transactional
    public Result<String> create(@RequestBody @Valid Team team, HttpServletRequest request) {
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        team.setLeaderId(userId);
        // 检查当前用户状态
        Users user = userMapper.selectById(userId);
        if (user == null || !"normal".equals(user.getAccountStatus())) {
            return Result.error("您的账号状态异常，无法创建队伍");
        }
        String fileError = fileAccessPolicy.validatePublicFile(team.getTeamImageId(), SecurityUtils.currentUser(request), true);
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(team.getIntroduce(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);

        if (team.getTeamName() != null) {
            String trimmedName = team.getTeamName().trim();
            if (trimmedName.isEmpty()) {
                return Result.error("队伍名称不能为空");
            }
            // 将处理后的名称重新写回 team 对象，确保落库的是干净的数据
            team.setTeamName(trimmedName); 
        }

        if (team.getTeamName() != null && team.getTeamName().length() > 15) {
            return Result.error("队伍名称不能超过15个字");
        }

        teamMapper.insert(team);
        TeamMember member = new TeamMember();
        member.setTeamId(team.getTeamId());
        member.setMemberId(userId);
        member.setStatus("1");
        teamMemberMapper.insert(member);
        return Result.success("创建成功");
    }

    @GetMapping("/my-teams")
    public Result<List<TeamVO>> myTeams(HttpServletRequest request) {
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        List<TeamMember> relations = teamMemberMapper.selectList(new QueryWrapper<TeamMember>().eq("member_id", userId));
        List<TeamVO> voList = new ArrayList<>();
        for (TeamMember relation : relations) {
            Team team = teamMapper.selectById(relation.getTeamId());
            if (team == null) continue;
            TeamVO vo = new TeamVO();
            BeanUtils.copyProperties(team, vo);
            vo.setMyStatus(relation.getStatus());

            if (team.getTeamImageId() != null) {
                FileSystem fs = fileSystemMapper.selectById(team.getTeamImageId());
                if (fs != null) vo.setTeamAvatarUrl("/api" + fs.getUrl());
            }

            List<TeamMemberVO> memberVos = new ArrayList<>();
            // Pending invitations reveal the team summary only.
            if ("1".equals(relation.getStatus())) {
                boolean leaderView = userId.equals(team.getLeaderId());
                List<TeamMember> membersRel = teamMemberMapper.selectList(new QueryWrapper<TeamMember>()
                        .eq("team_id", team.getTeamId()).eq("status", "1"));
                for (TeamMember tm : membersRel) {
                    Users u = userMapper.selectById(tm.getMemberId());
                    if (u == null) continue;
                    TeamMemberVO member = new TeamMemberVO();
                    member.setUserId(u.getUserId());
                    member.setUserName(u.getUserName());
                    if (u.getAvatarFileId() != null) member.setAvatarUrl("/api/file/image/" + u.getAvatarFileId());
                    boolean identityView = leaderView || userId.equals(u.getUserId());
                    member.setRealName(identityView ? u.getRealName() : maskName(u.getRealName()));
                    if (identityView) member.setNumberId(u.getNumberId());
                    memberVos.add(member);
                }
            }
            vo.setMembers(memberVos);
            Users leader = userMapper.selectById(team.getLeaderId());
            if (leader != null) vo.setLeaderName(leader.getUserName());
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @PostMapping("/update")
    @Transactional
    public Result<String> update(@RequestBody @Valid Team team, HttpServletRequest request) {
        if (team.getTeamId() == null) return Result.error("队伍ID不能为空");
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");

        Team oldTeam = teamMapper.selectById(team.getTeamId());
        if (oldTeam == null) return Result.error("队伍不存在");

        if (!oldTeam.getLeaderId().equals(currentUserId)) {
            return Result.error("无权修改");
        }
        
        Long oldImageId = oldTeam.getTeamImageId();
        boolean needDelete = false;

        if (team.getTeamImageId() != null && !team.getTeamImageId().equals(oldImageId)) {
            String error = fileAccessPolicy.validatePublicFile(team.getTeamImageId(), SecurityUtils.currentUser(request), true);
            if (error != null) return Result.error(error);
            oldTeam.setTeamImageId(team.getTeamImageId());
            needDelete = (oldImageId != null);
        }

        if (team.getTeamName() != null) {
            String trimmedName = team.getTeamName().trim();

            // 只有当去除了首尾空格后的新名称，和原来的名称不一样时，才进行更新和查重
            if (!trimmedName.equals(oldTeam.getTeamName())) {
                if (trimmedName.isEmpty()) {
                    return Result.error("队伍名称不能为空");
                }
                if (trimmedName.length() > 15) {
                    return Result.error("队伍名称不能超过15个字");
                }
                oldTeam.setTeamName(trimmedName); // 更新为去除了首尾空格的新名称
            }
        }
        if (team.getIntroduce() != null) {
            String error = fileAccessPolicy.validatePublicContent(team.getIntroduce(), SecurityUtils.currentUser(request));
            if (error != null) return Result.error(error);
            oldTeam.setIntroduce(team.getIntroduce());
        }

        teamMapper.updateById(oldTeam);

        if (needDelete) {
            fileAccessPolicy.deleteIfUnreferenced(oldImageId, SecurityUtils.currentUser(request));
        }

        return Result.success("修改成功");
    }

    @GetMapping("/search-user")
    public Result<List<UserSearchVO>> searchUser(@RequestParam String keyword) {
        if (!StringUtils.hasText(keyword)) return Result.success(List.of());
        List<Users> users = userMapper.selectList(new QueryWrapper<Users>()
                .like("user_name", keyword.trim()).eq("account_status", "normal").last("LIMIT 20"));
        return Result.success(users.stream().map(SecurityUtils::publicSearchUser).toList());
    }

    @PostMapping("/invite")
    public synchronized Result<String> invite(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        try {
            if (params.get("teamId") == null || params.get("targetUserId") == null) {
                return Result.error("参数缺失");
            }

            Long teamId = Long.valueOf(params.get("teamId").toString());
            Long inviterId = SecurityUtils.currentUserId(request);
            if (inviterId == null) return Result.error("未登录或会话已失效");
            Long targetUserId = Long.valueOf(params.get("targetUserId").toString());

            Users inviter = userMapper.selectById(inviterId);
            if (inviter == null) return Result.error("邀请人不存在");
            if (!"normal".equals(inviter.getAccountStatus())) return Result.error("账号状态异常，无法发送邀请");

            Users targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) return Result.error("用户不存在");
            if (!"normal".equals(targetUser.getAccountStatus())) return Result.error("对方账号状态异常，无法邀请");

            Team team = teamMapper.selectById(teamId);
            if (team == null) return Result.error("队伍不存在");

            if (!team.getLeaderId().equals(inviterId)) return Result.error("只有队长可以邀请");

            Long count = teamMemberMapper.selectCount(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("status", "1"));
            if (count >= 5) return Result.error("队伍已满员");

            Long exists = teamMemberMapper.selectCount(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("member_id", targetUserId));
            if (exists > 0) return Result.error("该用户已在队中或已发送邀请");

            TeamMember tm = new TeamMember();
            tm.setTeamId(teamId);
            tm.setMemberId(targetUserId);
            tm.setStatus("0");
            teamMemberMapper.insert(tm);

            Users target = userMapper.selectById(targetUserId);
            if (target != null && StringUtils.hasText(target.getEmail())) {
                try {
                    emailService.sendTeamInviteEmail(target.getEmail(), inviter.getRealName(), team.getTeamName());
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(TeamController.class).warn("操作失败，请检查请求参数和服务配置");
                }
            }

            return Result.success("邀请已发送");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TeamController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("邀请失败: " + e.getMessage());
        }
    }

    @PostMapping("/handle-invite")
    public Result<String> handleInvite(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long teamId = Long.valueOf(params.get("teamId").toString());
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");
        boolean accept = Boolean.parseBoolean(params.get("accept").toString());

        if (accept) {
            Users user = userMapper.selectById(userId);
            if (user == null) return Result.error("用户不存在");
            if (!"normal".equals(user.getAccountStatus())) return Result.error("账号状态异常，无法加入队伍");
        }

        QueryWrapper<TeamMember> wrapper = new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("member_id", userId).eq("status", "0");
        List<TeamMember> list = teamMemberMapper.selectList(wrapper);

        if (list == null || list.isEmpty()) return Result.error("邀请不存在或已处理");

        TeamMember tm = list.get(0);
        // 修复重复邀请数据问题：如果有重复记录，保留第一条，删除其余的
        if (list.size() > 1) {
            List<Long> duplicateIds = list.stream().skip(1).map(TeamMember::getTeamMemberId).collect(Collectors.toList());
            teamMemberMapper.deleteBatchIds(duplicateIds);
        }

        Team team = teamMapper.selectById(teamId);

        if (accept) {
            Long count = teamMemberMapper.selectCount(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("status", "1"));
            if (count >= 5) return Result.error("队伍已满员，无法加入");

            tm.setStatus("1");
            teamMemberMapper.updateById(tm);

            Users leader = userMapper.selectById(team.getLeaderId());
            Users invitee = userMapper.selectById(userId);
            if (leader != null && invitee != null) {
                emailService.sendInviteResponseEmail(leader.getEmail(), invitee.getRealName(), team.getTeamName(), true);
            }
            com.cwnupaa.backend.service.RatingRefreshRequested.publish(ratingEvents);
            return Result.success("已加入队伍");
        } else {
            teamMemberMapper.deleteById(tm.getTeamMemberId());
            Users leader = userMapper.selectById(team.getLeaderId());
            Users invitee = userMapper.selectById(userId);
            if (leader != null && invitee != null) {
                emailService.sendInviteResponseEmail(leader.getEmail(), invitee.getRealName(), team.getTeamName(), false);
            }
            return Result.success("已拒绝邀请");
        }
    }

    @PostMapping("/remove")
    public Result<String> removeMember(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long teamId = params.get("teamId");
        Long memberId = params.get("memberId");
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");

        Team team = teamMapper.selectById(teamId);
        if (team == null) return Result.error("队伍不存在");

        if (memberId.equals(team.getLeaderId())) return Result.error("队长不能移除自己"); // 队长只能解散，但现在禁止解散

        if (!operatorId.equals(team.getLeaderId()) && !operatorId.equals(memberId)) {
            return Result.error("无权操作");
        }

        Users member = userMapper.selectById(memberId);
        Users operator = userMapper.selectById(operatorId);
        boolean isKicked = operatorId.equals(team.getLeaderId()) && !memberId.equals(operatorId);

        teamMemberMapper.delete(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("member_id", memberId));

        if (isKicked && member != null && operator != null) {
            emailService.sendTeamRemovalEmail(member.getEmail(), member.getRealName(), team.getTeamName(), operator.getRealName());
        } else if (!isKicked && operator != null) {
            Users leader = userMapper.selectById(team.getLeaderId());
            if (leader != null) {
                emailService.sendMemberLeaveEmail(leader.getEmail(), member.getRealName(), team.getTeamName());
            }
        }

        com.cwnupaa.backend.service.RatingRefreshRequested.publish(ratingEvents);
        return Result.success("操作成功");
    }

    @PostMapping("/disband")
    @Transactional
    public Result<String> disband(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long teamId = params.get("teamId");
        Long operatorId = SecurityUtils.currentUserId(request);
        if (operatorId == null) return Result.error("未登录或会话已失效");

        Team team = teamMapper.selectById(teamId);
        if (team == null) return Result.error("队伍不存在");

        if (!team.getLeaderId().equals(operatorId)) return Result.error("只有队长可以解散队伍");

        // 删除队员关联
        teamMemberMapper.delete(new QueryWrapper<TeamMember>().eq("team_id", teamId));
        
        // 删除队伍
        teamMapper.deleteById(teamId);
        
        // 删除头像文件（如果有）
        if (team.getTeamImageId() != null) {
            fileAccessPolicy.deleteIfUnreferenced(team.getTeamImageId(), SecurityUtils.currentUser(request));
        }

        com.cwnupaa.backend.service.RatingRefreshRequested.publish(ratingEvents);
        return Result.success("队伍已解散");
    }

    private static String maskName(String name) {
        if (!StringUtils.hasText(name)) return null;
        int first = name.offsetByCodePoints(0, 1);
        return name.substring(0, first) + "*";
    }
}
