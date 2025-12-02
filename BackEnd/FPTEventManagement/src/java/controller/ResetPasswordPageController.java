package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Simple reset page served by backend as a fallback when frontend is not running.
 * URL: /reset-pass?token=...
 */
@WebServlet("/reset-pass")
public class ResetPasswordPageController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        String token = req.getParameter("token") == null ? "" : req.getParameter("token");
        String ctx = req.getContextPath();

        try (PrintWriter out = resp.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<title>Reset Password - FPT Event Ticketing System</title>");
            out.println("<style>body{font-family:Arial,Helvetica,sans-serif;padding:20px;color:#333} input{padding:8px;margin:6px 0;width:100%;box-sizing:border-box} button{padding:10px 14px;background:#2563eb;color:#fff;border:none;border-radius:6px}</style>");
            out.println("</head><body>");
            out.println("<h2>Đặt lại mật khẩu - FPT Event Ticketing System</h2>");
            out.println("<p>Nếu bạn đã nhận được email chứa token, hãy dán token vào ô dưới hoặc nhấp vào liên kết từ email để mở trang này.</p>");
            out.println("<label>Token (tự động nếu có):</label>");
            out.println("<input id=\"token\" value=\"" + escapeHtml(token) + "\" />");
            out.println("<label>Mã OTP (từ email):</label>");
            out.println("<input id=\"otp\" placeholder=\"123456\" />");
            out.println("<label>Mật khẩu mới:</label>");
            out.println("<input id=\"newPassword\" type=\"password\" placeholder=\"Mật khẩu mới\" />");
            out.println("<div style='margin-top:12px'><button id=\"btn\">Gửi</button></div>");
            out.println("<div id=\"msg\" style='margin-top:12px;color:#a00'></div>");

            out.println("<script>");
            out.println("const btn=document.getElementById('btn');");
            out.println("btn.addEventListener('click', async ()=>{const token=document.getElementById('token').value;const otp=document.getElementById('otp').value;const newPassword=document.getElementById('newPassword').value; if(!token||!otp||!newPassword){document.getElementById('msg').textContent='Vui lòng điền đủ token, otp và mật khẩu mới.';return;} try{const res=await fetch('"+ctx+"/api/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token,otp,newPassword})});const txt=await res.text(); if(res.ok){document.getElementById('msg').style.color='green';document.getElementById('msg').textContent='Đặt lại mật khẩu thành công.';} else {document.getElementById('msg').style.color='#a00';document.getElementById('msg').textContent=txt;} }catch(e){document.getElementById('msg').textContent='Lỗi: '+e.message}});" );
            out.println("</script>");
            out.println("</body></html>");
        }
    }

    private String escapeHtml(String s){
        if(s==null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace('"','\'');
    }
}
