---
title : Play 2: Using Action Composition for Easy Authentication and Authorization
tags : [scala, play2]
---

I've been working on a number of projects using the Play 2 framework and one feature in particular that I've been able
to put to good use is "action composition". Action composition allows for a very flexible way to ensure preconditions
and postconditions are properly met on an endpoint by endpoint basis. One usage that it lends itself very well to is
authentication and authorization. The majority of this blog post will be structured to illustrate that particular use 
case, but action composition is flexible enough to handle many different scenarios.  

## Action Composition

So, what is action composition? Well let's start with a "normal" action.

```scala
    def normalAction = Action { request =>
        // Normal Stuff
        Ok("Normal Response")
    }
```

An action is basically a function of type: 

```scala
    (Request[A] => Result)
```

Action composition is facilitated by providing a higher order function that takes an Action function
and returns an Action function. Pretty simple right?

```scala
    def EnhancedAction(f: (Request[AnyContent] => Result)) = Action { request =>
        // Do Something
        f(request)
    }

    def enhancedNormalAction = EnhancedAction { request =>
        // Normal Stuff
        Ok("Normal Response")
    }
```

This allows us to do almost anything before and/or after calling an action function including high-jacking the response
if it makes sense. This could be used to ensure proper authorization, that something exists, performing some type of setup,
or perhaps some type of cleanup. There are a large number of applications for Action Composition, and when paired with
wrapped requests (which I'll discuss in a bit), it becomes an even more powerful tool.  

## The Problem

In order to illustrate how action composition can be used to solve some concrete problems, I've created an (extremely
contrived) real world problem. In this case we have an application that hosts surveys for various companies which users
can fill out and submit.

The constraints for the application are:

- Solely uses facebook authentication
- Companies have admins which can create / edit surveys
- Users need authorization to view surveys for that company
- Users need authorization to fill out surveys on a per survey basis

I realize that this authorization structure doesn't make perfect sense, but it will do just fine to show off the some of the 
solutions that can be achieved with action composition.

All source code for this project can be found at [my github repo](https://github.com/gilbertw1/play2-action-comp-auth-example).

First thing we'll start with is the routes file, this will give us a sense of the structure of the application:

```scala
    # Application Base
    GET     /                                                           controllers.Application.index
    GET     /fbOAuth                                                    controllers.Application.fbOAuth
    GET     /fbOAuthReturn                                              controllers.Application.fbOAuthReturn(state: String, code: String)

    # Companies
    GET     /companies                                                  controllers.Companies.list
    GET     /companies/create                                           controllers.Companies.create()
    GET     /companies/:id                                              controllers.Companies.view(id: Long)
    GET     /companies/:id/update                                       controllers.Companies.update(id: Long)
    GET     /companies/:id/admin                                        controllers.Companies.admin(id: Long)

    # Surveys
    GET     /companies/:cid/surveys                                     controllers.Surveys.list(cid: Long)
    GET     /companies/:cid/surveys/create                              controllers.Surveys.create(cid: Long)
    GET     /companies/:cid/surveys/:id                                 controllers.Surveys.view(cid: Long, id: Long)
    GET     /companies/:cid/surveys/:id/update                          controllers.Surveys.update(cid: Long, id: Long)
    GET     /companies/:cid/surveys/:id/fillOut                         controllers.Surveys.fillOut(cid: Long, id: Long)  
```


## Facebook Authentication

The first requirement that we need to fulfill is the applicaton needs to use Facebook authentication to log a user in. Take a moment
to notice the fbOAuth and fbOAuthReturn endpoints. 

The first "Action Composer" we'll create will take on the responsibility of
verifying that the user has granted our application access to Facebook, and if they have not it will prompt them to do so before
forwarding to the action being composed. It will create a new wrapped request type and put the facebook info into the request. 
Additionally it will add the user's facebook information to the signed session cookie provided by the Play Framework.

As with all of the action composers we will create, they will be created in a trait that all other controllers will be extending.

```scala
    def FacebookAuthenticated(f: FacebookAuthenticatedRequest => Result) = {
        Action { request =>
            val session = request.session                                           // 1
            Facebook.retrieveFacebookUserFromSession(session) match {               // 2
                case Some(fbUserInfo: FacebookUser) => 
                    f(FacebookAuthenticatedRequest(fbUserInfo, request))            // 3
                case None => 
                    val newSession = session + ("PostOAuthUrl" -> request.uri)      // 4
                    Redirect(routes.Application.fbOAuth()).withSession(newSession)
            }
        }
    }

    case class FacebookAuthenticatedRequest (val fbUserInfo: FacebookUser, request: Request[AnyContent]) 
        extends WrappedRequest(request)
```

As can be seen in the above code sample, here we are creating a function that takes another function of type: 

```scala
    (FacebookAuthenticatedRequest => Result) 
```

I'll mention now that a wrapped request is a request that can be extended to provide additional values. This way we'll be able to easily
extend the request in a typesafe manner, as in this example by adding a "FacebookUser" containing vital facebook information that we'll use
in our actions.

This particular action is a good example of using action composition to potentially high-jack the response in certain cases. The basic flow
of the above example is:

1. Get the session
2. Check if facebook user information is currently stored in the session cookie
3. If we find facebook information then we construct the FacebookAuthenticatedRequest and call the composed action with it
4. Otherwise we add the currently requested uri to session and proceed to authenticate the user with facebook.

If you look at the source code, you'll see that at the end of the facebook authentication cycle the code checks to see if there is a 
"PostOAuthUrl" in the session and forwards to it. This way once we get into the meat of a function that composes within this 
"FacebookAuthenticated" function we can always be sure that they have authenticated with facebook. This can be used anywhere within
the app:

```scala
    class MyController extends BaseController {
        def facebookProtectedHome = FacebookAuthenticated { implicit request =>
            // We can be confident that we are facebook authenticated in here.
            Ok(html.views.facebookAuthenticatedView())
        }
    }
```

This is pretty simple right? We've now implemented basic facebook authentication within our app. You may notice that I've marked the 
request parameter as implicit, I'll go into why we're doing this in a moment, but for now just make a small mental note of it.  

## User Authentication

So, did I mention that action composers compose as well? Well now lets implement User Authentication using our newly created 
"FacebookAuthenticated" composer:

```scala
    def UserAuthenticated(f: UserAuthenticatedRequest => Result) = {
        FacebookAuthenticated { request =>
            request.session.get("authenticatedUser") match {
                case Some(userJson) =>
                    f(UserAuthenticatedRequest(User.deserialize(userJson), request))
                case None =>
                    val fbId = request.fbUserInfo.id
                    val user = User.findByFacebookId(fbId).getOrElse(createUserFromFacebookUser(request.fbUserInfo))
                    f(UserAuthenticatedRequest(user, request)).asInstanceOf[PlainResult].withSession (
                        request.session + ("authenticatedUser" -> User.serialize(user))
                    )
            }
        }
    }

    case class UserAuthenticatedRequest (val user: User, request: Request[AnyContent]) 
        extends WrappedRequest(request)
```

You'll notice that this action composer is in a very similar vein to the "FacebookAuthenticated" composer. It basically
checks to see if there is an authenticated user existing in the session cookie and forwards to the composed action if there
is. Otherwise it looks up the user by the facebook Id and creates it if it does not exist, adds it to the session cookie, 
and then forwards to the action.

This can be used in our application to ensure a user is authenticated before viewing a list of companies in our system:

```scala
    object Companies extends BaseController {
        def list = UserAuthenticated { implicit request =>
            // User is created and / or authenticated
            Ok(views.html.company.list(Company.all()))
        }
    }
```

## Authorization

I think at this point you can start to see where this is headed. We'll go and ahead and implement the action composers that we'll
need to create the rest of our endpoints now. We'll need to authorize at the company admin level, the company user level, and the 
survey level:

```scala
    def CompanyAdminAuthenticated(companyId: Long)(f: CompanyAdminAuthenticatedRequest => Result) = {
        UserAuthenticated { request =>
            val companyOpt = Company.findById(companyId)
            val user = request.user
            companyOpt match {
                case Some(company) if User.isCompanyAdmin(user, company.id.get) => 
                    f(CompanyAdminAuthenticatedRequest(user, company, request))
                case _ => Unauthorized
            }
        }
    }

    def CompanyAuthenticated(companyId: Long)(f: CompanyAuthenticatedRequest => Result) = {
        UserAuthenticated { request =>
            val companyOpt = Company.findById(companyId)
            val user = request.user
            companyOpt match {
                case Some(company) if User.isCompanyMember(user, company.id.get) => 
                    f(CompanyAuthenticatedRequest(user, company, request))
                case _ => Unauthorized
            }
        }
    }

    def SurveyAuthenticated(companyId: Long, surveyId: Long)(f: SurveyAuthenticatedRequest => Result) = {
        CompanyAuthenticated(companyId) { request =>
            val surveyOpt = Survey.findById(surveyId)
            val user = request.user
            surveyOpt match {
                case Some(survey) if User.canAccessSurvey(user, survey.id.get) => 
                    f(SurveyAuthenticatedRequest(user, request.company, survey, request))
                case _ => Unauthorized
            }
        }
    }

    case class SurveyAuthenticatedRequest (val user: User, val company: Company, val survey: Survey, request: Request[AnyContent]) 
        extends WrappedRequest(request)
    case class CompanyAuthenticatedRequest (val user: User, val company: Company, request: Request[AnyContent]) 
        extends WrappedRequest(request)
    case class CompanyAdminAuthenticatedRequest (val user: User, val company: Company, request: Request[AnyContent]) 
        extends WrappedRequest(request)
```

As you can see we're using currying to pass parameters to each composer as well as the action that we are composing. Addtionally we've created
some more request types to forward along the the composed actions. These should all be fairly straight forward at this point, they're basically
authorizing the user based on whether they are a user of the company, an admin, or a user that can see a specific survey. 

Although it isn't implemented this way, we could easily be storing information in the session cookie, that would prevent needing to hit the database
on future invocations of this action composer. 

Each of these composers can simply be used as:

```scala
    // In Companies Controller
    def admin(id: Long) = CompanyAdminAuthenticated(id) { implicit request =>
        Ok(views.html.company.admin())
    }

    // In Companies Controller
    def view(id: Long) = CompanyAuthenticated(id) { implicit request =>
        Ok(views.html.company.view())
    }

    // In Surveys Controller
    def fillOut(companyId: Long, surveyId: Long) = SurveyAuthenticated(companyId, surveyId) { implicit request =>
        Ok(views.html.survey.fillOut())
    }
```

As you can see at this point we can create a new Action that is either FacebookAuthenticated, UserAuthenticated, CompanyAdminAuthenticated,
CompanyAuthenticated, or SurveyAuthenticated. How cool is that? We've basically removed all the cognitive load of having to think about
how to reach any of these states when creating a new action. We are able to just assume a "perfect world" inside each of the above actions,
and not really worry about how we got there.  

## Authentication / Authorization In The Views

So, at this point you might have noticed that we're creating views without any parameters (At least any explicit ones!). Well the fact of the
matter is that we ARE actually passing parameters to the views. If you look at all the above examples, you'll see that we're putting the request
in implict scope when we're calling the views.

This allows us to create views that have specific request types as implicit parameters and have them checked at compile time.

views/company/list.scala.html:

```scala
    @(companies: List[Company])(implicit request: UserAuthenticatedRequest) {
        <h1>List Companies Page</h1>
    }
```

views/company/admin.scala.html:

```scala
    @()(implicit request: CompanyAdminAuthenticatedRequest) {
        <h1>Admin Page for @request.company.name</h1>
    }
```

views/company/view.scala.html:

```scala
    @()(implicit request: CompanyAuthenticatedRequest) {
        <h1>View @request.company.name</h1>
    }
```

views/survey/fillOut.scala.html:

```scala
    @()(implicit request: SurveyAuthenticatedRequest) {
        <h1>Fill out @request.survey.name for company @request.company.name</h1>
    }
```

As you'll notice, each of these views declare what type of request they require. So, if we were to try for example to render the company
admin page without using an "CompanyAdminAuthenticatedAction" that passes along a "CompanyAdminAuthenticatedRequest" then our application
would fail to compile, thereby preventing us from accidentally rendering an admin page when a user is not authenticated. This principle
holds true for every other page in our application. 

Additionally given the fact that all the info required to render most pages, especially admin or authenticated pages, are passed along in
the specific request, it also prevents us from rendering the wrong information. For example, since the admin page uses the company object
off of the "CompanyAdminAuthenticatedRequest" then it will only render sensitive information about that specific company to the admin. This
in my opinion prevents a whole class of possible errors.  

## Conclusion

So there you have it. Action composition has served me immensely in creating Play 2 applications by allowing me to encapsulate certain concerns
in reusable "action composers". I've used code similar to the above in several projects and it has served me to great effect. Hope it helps 
you in some way. 

Thanks for reading. You can find the source code used in this post here: [https://github.com/gilbertw1/play2-action-comp-auth-example](https://github.com/gilbertw1/play2-action-comp-auth-example)