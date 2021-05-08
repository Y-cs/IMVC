package self.demo.controller;


import self.demo.service.IDemoService;
import self.mvc.annotations.Autowired;
import self.mvc.annotations.Controller;
import self.mvc.annotations.RequestMapping;
import self.mvc.annotations.Security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@Security("b")
@RequestMapping("/self/demo")
public class DemoController {


    @Autowired
    private IDemoService demoService;


    /**
     * URL: /self.demo/query?name=lisi
     * @param request
     * @param response
     * @param name
     * @return
     */
    @Security({"a","C"})
    @RequestMapping("/query")
    public String query(HttpServletRequest request, HttpServletResponse response,String name) {
        return demoService.get(name);
    }
}
