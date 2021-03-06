package com.lastpang.server.Controller;

import com.lastpang.server.Domain.Member;
import com.lastpang.server.Domain.Menu;
import com.lastpang.server.Domain.Ordering;
import com.lastpang.server.Domain.Store;
import com.lastpang.server.Repository.MemberRepository;
import com.lastpang.server.Repository.MenuRepository;
import com.lastpang.server.Repository.OrderRepository;
import com.lastpang.server.Repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

@EnableScheduling
@RequiredArgsConstructor
@RestController
public class MainController {
    private Logger logger = LoggerFactory.getLogger(ApplicationRunner.class);
    @Value("${static.resource.location}")
    private String menuImgLocation;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private OrderRepository orderRepository;


    private final SimpMessageSendingOperations messagingTemplate;

    @PostMapping(path="/auth/register")
    public Map<String, Object> addNewUser (@RequestBody Member member) {
        String username = member.getUsername();

        Map<String, Object> map = new HashMap<>();
        logger.info("회원가입요청 아이디: "+username + ", 비번: " + member.getPassword());

        memberRepository.save(member);
        map.put("errorCode", 10);

        return map;
    }

    public static Object convertMapToObject(Map<String,Object> map,Object obj){
        String keyAttribute = null;
        String setMethodString = "set";
        String methodString = null;
        Iterator itr = map.keySet().iterator();

        while(itr.hasNext()){
            keyAttribute = (String) itr.next();
            methodString = setMethodString+keyAttribute.substring(0,1).toUpperCase()+keyAttribute.substring(1);
            Method[] methods = obj.getClass().getDeclaredMethods();
            for(int i=0;i<methods.length;i++){
                //System.out.println(methods[i].getName());
                if(methodString.equals(methods[i].getName())){
                    try{
                        methods[i].invoke(obj, map.get(keyAttribute));
                    }catch(Exception e){
                        //e.printStackTrace();
                    }
                }
            }
        }
        return obj;
    }

    @PostMapping(path="/store/register")
    public Map<String, Object> addNewStore (@RequestBody Store store) {
        String username = store.getUsername();
        String name = store.getStoreName();
        logger.info("가게등록요청 이름: "+name);
        store.setMember(memberRepository.findMemberByUsername(username));
        Map<String, Object> map = new HashMap<>();
        storeRepository.save(store);
        map.put("errorCode", 10);
        return map;
    }

    @GetMapping(path="/stores/{username}")
    public Map<String, Object> getStoresOfUser(@PathVariable String username) {
        Map<String, Object> map = new HashMap<>();
        List<Store> sl = storeRepository.findStoresByMember_Username(username);
        map.put("errorCode", 10);
        map.put("stores", sl);
        return map;
    }


    @GetMapping(path="/store/{storeId}")
    public Map<String, Object> getStore(@PathVariable Integer storeId) {
        Map<String, Object> map = new HashMap<>();
        Store s = storeRepository.findStoreByStoreId(storeId);
        map.put("errorCode", 10);
        map.put("store", s);
        return map;
    }


    @PostMapping(path = "/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> m) throws Exception {
        Map<String, Object> map = new HashMap<>();
        final String username = m.get("username");
        //logger.info("test input username: " + username);

        Member member = memberRepository.findMemberByUsername(username);
        member.setAccess_dt(new Date());
        memberRepository.save(member);
        map.put("errorCode", 10);
        return map;
    }


    //ORDER
    @PostMapping(path = "/order/new")
    public void newOrder(@RequestBody Map<String, Object> m) throws Exception {
        Ordering order = new Ordering();
        order.setOrderStatus(0);
        order.setStore(storeRepository.findStoreByStoreId((Integer)m.get("storeId")));
        order.setOrder_dt(new Date());
        order.setRequest((String)m.get("request"));
        order.setMember(memberRepository.findMemberByUsername((String)m.get("username")));
        ArrayList<Object> menuone =(ArrayList<Object>)((Map<String, Object>)m.get("orderList")).get("menu1");

        order.setMenu1((String)menuone.get(0));
        order.setQuantity1((Integer)menuone.get(1));
        order.setPrice1((Integer)menuone.get(2));


        order.setTotalPrice((Integer)m.get("totalPrice"));
        System.out.println(m);

        orderRepository.save(order);
        String s = Integer.toString((Integer)m.get("storeId"));
        messagingTemplate.convertAndSend("/topic/"+s, order);
    }

    @GetMapping(path="/order/{storeId}")
    public Map<String, Object> getOrder(@PathVariable Integer storeId) {
        Map<String, Object> map = new HashMap<>();
        List<Ordering> o = orderRepository.findOrderingsByStore_StoreId(storeId);
        map.put("errorCode", 10);
        map.put("store", o);
        return map;
    }



    //MENU

    @PostMapping(value = "/menu/upload")
    public Map<String, Object> upload(@RequestParam("storename") String storename, @RequestParam("file") MultipartFile multipartFile,
                                      @RequestParam("menuname") String menuname, @RequestParam(value = "price", required = false) Integer price,
                                      @RequestParam(value="desc", required = false) String desc, @RequestParam(value = "options", required = false) String options) {
        UUID uid = UUID.randomUUID();
        File targetFile = new File(menuImgLocation+uid.toString());
        Menu menu = new Menu();
        System.out.println("actual path is: " + targetFile.getAbsolutePath());
        try {
            InputStream fileStream = multipartFile.getInputStream();
            FileUtils.copyInputStreamToFile(fileStream, targetFile);
            String filename = multipartFile.getOriginalFilename();
            menu.setMenuImgUuid(uid.toString());
            menu.setPrice(price);
            menu.setDescription(desc);
            menu.setStore(storeRepository.findStoreByStoreName(storename));
            menu.setOptions(options);
            menu.setMenuName(menuname);
            menuRepository.save(menu);

        } catch (IOException e) {
            FileUtils.deleteQuietly(targetFile);
            e.printStackTrace();
        }

        Map<String, Object> m = new HashMap<>();
        m.put("errorCode", 10);
        return m;
    }

    @GetMapping(value="/menus/{storename}")
    public Map<String, Object> getMenusOfStore(@PathVariable String storename) {
        Map<String, Object> map = new HashMap<>();
        List<Menu> ml = menuRepository.findMenusByStore_StoreName(storename);
        map.put("errorCode", 10);
        map.put("menus", ml);
        return map;
    }


    @GetMapping(path="/menu/{menuId}")
    public Map<String, Object> getMenu(@PathVariable Integer menuId) {
        Map<String, Object> map = new HashMap<>();
        Menu m = menuRepository.findByMenuId(menuId);
        map.put("errorCode", 10);
        map.put("menu", m);
        return map;
    }


}
