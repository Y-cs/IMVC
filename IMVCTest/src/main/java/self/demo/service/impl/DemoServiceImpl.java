package self.demo.service.impl;


import self.demo.service.IDemoService;
import self.mvc.annotations.Service;

@Service("demoService")
public class DemoServiceImpl implements IDemoService {
    @Override
    public String get(String name) {
        System.out.println("service 实现类中的name参数：" + name) ;
        return name;
    }
}
