package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.HistoricalAwardService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/team-space/history")
public class HistoricalAwardController {
    private final HistoricalAwardService service;
    public HistoricalAwardController(HistoricalAwardService service) {this.service=service;}
    private UserProfileVO actor(HttpServletRequest request) {
        var user=SecurityUtils.currentUser(request);
        if(user==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录");
        if(!"normal".equals(user.getAccountStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请先完成学生身份认证");
        return user;
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<String>> error(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Result.error(e.getStatusCode().value(),e.getReason()));}
    @GetMapping("/catalog")
    public Result<?> catalog(HttpServletRequest req,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String uid,@RequestParam(defaultValue="false") boolean mine) {return Result.success(service.list(actor(req),keyword,uid,mine));}
    @GetMapping("/claims")
    public Result<?> claims(HttpServletRequest req,@RequestParam(defaultValue="false") boolean review) {return Result.success(service.claims(actor(req),review));}
    @PostMapping("/claims")
    public Result<?> claim(HttpServletRequest req,@RequestBody HistoricalAwardService.ClaimInput input) {return Result.success(service.claim(actor(req),input));}
    @PostMapping("/claims/{id}/withdraw")
    public Result<?> withdraw(HttpServletRequest req,@PathVariable long id,@RequestBody HistoricalAwardService.Decision input) {service.withdraw(actor(req),id,input.version());return Result.success("已撤回认领申请");}
    @PostMapping("/claims/{id}/review")
    public Result<?> review(HttpServletRequest req,@PathVariable long id,@RequestBody HistoricalAwardService.Decision input) {service.review(actor(req),id,input);return Result.success(input.approved()?"已关联至成员个人档案":"已退回补充材料");}
    @PostMapping("/roster/preview")
    public Result<?> preview(HttpServletRequest req,@RequestBody HistoricalAwardService.ImportInput input) {return Result.success(service.preview(actor(req),input.rows()));}
    @PostMapping("/roster/import")
    public Result<?> importRoster(HttpServletRequest req,@RequestBody HistoricalAwardService.ImportInput input) {return Result.success(service.importRoster(actor(req),input));}
    @GetMapping("/links")
    public Result<?> links(HttpServletRequest req) {return Result.success(service.links(actor(req)));}
    @GetMapping("/links/{id}/audit")
    public Result<?> audit(HttpServletRequest req,@PathVariable long id) {return Result.success(service.audit(actor(req),id));}
    @PostMapping("/links/{id}/revoke")
    public Result<?> revoke(HttpServletRequest req,@PathVariable long id,@RequestBody HistoricalAwardService.Revoke input) {service.revoke(actor(req),id,input);return Result.success("已撤销归属，可重新核验名单");}
}
