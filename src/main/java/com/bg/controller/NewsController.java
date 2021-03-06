package com.bg.controller;

import com.bg.model.*;
import com.bg.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;
import com.bg.util.ToutiaoUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2016/7/10.
 */
@Controller
public class NewsController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    NewsService newsService;

    @Autowired
    QiniuService qiniuService;

    @Autowired
    HostHolder hostHolder;

    @Autowired
    UserService userService;

    @Autowired
    CommentService commentService;

    @Autowired
    LikeService likeService;
    @RequestMapping(path={"/news/{newsId}"},method = {RequestMethod.GET})
    public String newsDetail(@PathVariable("newsId") int newsId, Model model) {
        News news = newsService.getById(newsId);
        if(news!=null){
            int localUserId = hostHolder.getUser() != null ? hostHolder.getUser().getId() : 0;
            //System.out.println(localUserId);
            if (localUserId != 0) {
                model.addAttribute("like", likeService.getLikeStatus(localUserId, EntityType.ENTITY_NEWS, news.getId()));
                //System.out.println("1230+"+likeService.getLikeStatus(localUserId, EntityType.ENTITY_NEWS, news.getId()));
            } else {
                model.addAttribute("like", 0);
            }

            //comment
            List<Comment> comments = commentService.getCommentsByEntity(news.getId(), EntityType.ENTITY_NEWS);
            List<ViewObject> commentVOs = new ArrayList<>();
            for(Comment comment : comments){
                ViewObject vo =new ViewObject();
                vo.set("comment", comment);
                vo.set("user", userService.getUser(comment.getUserId()));
                commentVOs.add(vo);
            }
            model.addAttribute("comments", commentVOs);
        }

        model.addAttribute("news",news);
        model.addAttribute("owner", userService.getUser(news.getUserId()));
        return "detail";
    }

    @RequestMapping(path = {"/addComment"}, method = {RequestMethod.POST})
    public String addComment(@RequestParam("newsId") int newsId,
                             @RequestParam("content") String content) {
        try {
            //html 过滤
            content = HtmlUtils.htmlEscape(content);
            Comment comment = new Comment();
            comment.setUserId(hostHolder.getUser().getId());
            comment.setContent(content);
            comment.setEntityType(EntityType.ENTITY_NEWS);
            comment.setEntityId(newsId);
            comment.setStatus(0);
            comment.setCreatedDate(new Date());
            commentService.addComment(comment);

            //更新news里的评论数
            int count = commentService.getCommentCount(comment.getEntityId(),comment.getEntityType());
            newsService.updateCommentCount(comment.getEntityId(),count);
            //异步
        } catch (Exception e){
            logger.error("增加评论失败" + e.getMessage());
        }
        return "redirect:/news/" +  String.valueOf(newsId);
    }

    @RequestMapping(path={"/image"},method = {RequestMethod.GET})
    @ResponseBody
    public void getImage(@RequestParam("name") String imageName,
                           HttpServletResponse response) {
        try {
            response.setContentType("image/jpeg");
            StreamUtils.copy(new FileInputStream(new File(ToutiaoUtil.IMAGE_DIR + imageName)),response.getOutputStream());

        } catch (Exception e){
            logger.error("读取图片错误" + e.getMessage());
        }
    }


    @RequestMapping(path={"/uploadImage/"},method = {RequestMethod.POST})
    @ResponseBody
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            //System.out.println("in");
            //String fileUrl = newsService.saveImage(file);
            String fileUrl = qiniuService.svaeImage(file);
            if (fileUrl == null){
                return ToutiaoUtil.getJSONString(1,"上传失败");
            }
            return ToutiaoUtil.getJSONString(0,fileUrl);
        } catch (Exception e){
            logger.error("上传图片失败" + e.getMessage());
            return ToutiaoUtil.getJSONString(1,"上传失败");
        }
    }

    @RequestMapping(path={"/user/addNews/"},method = {RequestMethod.POST})
    @ResponseBody
    public String addNews(@RequestParam("image") String image,
                          @RequestParam("title") String title,
                          @RequestParam("link") String link) {
        try {
            News news = new News();
            if(hostHolder.getUser() != null){
                news.setUserId(hostHolder.getUser().getId());
            } else {
                //匿名id
                news.setUserId(0);
            }
            news.setImage(image);
            news.setLink(link);
            news.setTitle(title);
            news.setCreatedDate(new Date());
            newsService.addNews(news);
            return ToutiaoUtil.getJSONString(0,"添加资讯成功");
        } catch (Exception e) {
            logger.error("添加资讯错误" + e.getMessage());
            return ToutiaoUtil.getJSONString(1, "发布失败");
        }
    }

}
