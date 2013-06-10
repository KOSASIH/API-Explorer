package code.snippet

import net.liftweb.http.S
import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.common.Loggable
import net.liftweb.http.JsonHandler
import net.liftweb.json._
import net.liftweb.common.Full
import net.liftweb.common.Failure
import code.lib.Provider
import code.lib.OAuthClient
import net.liftweb.http.js.JsCmds.{Script, Noop}
import code.lib.ObpAPI
import code.lib.ObpJson._
import net.liftweb.util.CssSel
import net.liftweb.http.js.JsCmds.Replace
import scala.xml.NodeSeq
import net.liftweb.http.js.jquery.JqJsCmds.FadeOut
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.SetHtml
import scala.xml.Text
import net.liftweb.http.js.jquery.JqJsCmds.Show
import sun.reflect.generics.reflectiveObjects.NotImplementedException

case class PermissionsUrlParams(bankId : String, accountId: String)
case class ClickJson(userId: String, checked: Boolean, viewId : String)

class PermissionManagement(params : (PermissionsJson, AccountJson, PermissionsUrlParams)) extends Loggable {
  
  val permissionsJson = params._1
  val accountJson = params._2
  val urlParams = params._3
  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
  
  implicit val formats = DefaultFormats
  
  def rowId(userId: String) = "permission_row_" + userId
  
  val clickAjax = SHtml.ajaxCall(JsRaw("permissionsCheckBoxCallback(this)"), checkBoxClick)
  val removeAjax = SHtml.ajaxCall(JsRaw("this.getAttribute('data-userid')"), userId => {
    ObpAPI.removeAllPermissions(urlParams.bankId, urlParams.accountId, userId)
    Noop
  })

  def checkBoxClick(rawData : String) = {
    val data = tryo{parse(rawData).extract[ClickJson]}

    data match {
      case Full(d) => {
        if(d.checked) ObpAPI.addPermission(urlParams.bankId, urlParams.accountId, d.userId, d.viewId)
        else ObpAPI.removePermission(urlParams.bankId, urlParams.accountId, d.userId, d.viewId)
      }
      case Failure(msg, _, _) => logger.warn("Could not parse raw checkbox click data: " + rawData + ", " + msg)
      case _ => logger.warn("Could not parse raw checkbox click data: " + rawData)
    }
    
    Noop
  }
  
  val checkBoxJsFunc = JsRaw("""
    function permissionsCheckBoxCallback(checkbox) {
      var json = {
        "userId" : checkbox.getAttribute("data-userid"),
        "checked" : checkbox.checked,
        "viewId" : checkbox.getAttribute("data-viewid")
      }
      return JSON.stringify(json);
    }
    """).cmd
  
  
  def checkBox(permission : PermissionJson, view : String, userId : String) = {
    val onClick = "." + view + " [onclick]"
    val userIdData = "." + view + " [data-userid]"
    val viewIdData = "." + view + " [data-viewid]"
    
    val permissionExists = (for {
      views <- permission.views
    }yield {
      views.exists(_.id == (Some(view)))
    }).getOrElse(false)
    
    
    val checkedSelector : CssSel = 
      if(permissionExists) {{"." + view + " [checked]"} #> "checked"}
      else NOOP_SELECTOR
    
    checkedSelector &
    onClick #> clickAjax &
    userIdData #> userId &
    viewIdData #> view
  }

  def accountInfo = ".account-label *" #> accountJson.label.getOrElse("---")

  def manage = {

    permissionsJson.permissions match {
      case None => "* *" #> "No permissions exist"
      case Some(ps) => {
        ".callback-script" #> Script(checkBoxJsFunc) &
          ".row" #> {
            ps.map(permission => {
              val userId = permission.user.flatMap(_.id).getOrElse("")

              "* [id]" #> rowId(userId) &
                ".user *" #> permission.user.flatMap(_.display_name).getOrElse("") &
                checkBox(permission, "owner", userId) &
                checkBox(permission, "management", userId) &
                checkBox(permission, "ournetwork", userId) &
                checkBox(permission, "team", userId) &
                checkBox(permission, "board", userId) &
                checkBox(permission, "authorities", userId) &
                ".remove [data-userid]" #> userId &
                ".remove [onclick]" #> removeAjax
            })
          }
      }
    }

  }
  
  def addPermissionLink = {
    //TODO: Should generate this url instead of hardcode it
    "* [href]" #> {S.uri + "/create"}
  }
}