package com.heima.search.service.Impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.pojos.ApUserSearch;
import com.heima.search.pojos.dtos.HistorySearchDto;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.ApThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ApUserSearchServiceImpl implements ApUserSearchService {
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @Async
    public void insert(String keyword, Integer userId) {
        //1.查询当前用户的搜索关键词
        Query query = Query.query(Criteria.where("userId").is(userId).and("keyword").is(keyword));
        ApUserSearch apUserSearch = mongoTemplate.findOne(query, ApUserSearch.class);
        //2.存在 更新时间
        if (apUserSearch != null) {
            apUserSearch.setCreatedTime(new Date());
            mongoTemplate.save(apUserSearch);
            return;
        }
        //3.不存在 替换最后一条记录
        ApUserSearch newApUserSearch = new ApUserSearch();
        newApUserSearch.setUserId(userId);
        newApUserSearch.setKeyword(keyword);
        newApUserSearch.setCreatedTime(new Date());
        Query query1 = Query
                .query(Criteria.where("userId").is(userId))
                .with(Sort.by(Direction.DESC, "createdTime"));
        List<ApUserSearch> apUserSearchList = mongoTemplate.find(query1, ApUserSearch.class);
        if (null == apUserSearchList || apUserSearchList.size() < 10) {
            mongoTemplate.save(newApUserSearch);
        } else {
            ApUserSearch lastUserSearch = apUserSearchList.get(apUserSearchList.size() - 1);
            mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(lastUserSearch.getId())), newApUserSearch);
        }
    }

    @Override
    public ResponseResult findUserSearch() {
        ApUser user = ApThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        List<ApUserSearch> apUserSearchList = mongoTemplate.find(Query.query(
                        Criteria
                                .where("userId").is(user.getId()))
                .with(Sort.by(Direction.DESC, "createdTime")), ApUserSearch.class);
        return ResponseResult.okResult(apUserSearchList);
    }

    @Override
    public ResponseResult delUserSearch(HistorySearchDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser user = ApThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        mongoTemplate.remove(Query.query(Criteria
                .where("userId").is(user.getId())
                .and("id").is(dto.getId())), ApUserSearch.class);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
