# Kornerz Android — Screen Inventory

**Source of truth:** `kornerz/` (iOS app) — KornerzUniversal workspace
**Audit date:** 2026-10-07
**Audit method:** traced every screen class for external references from `SceneDelegate`, `AuthCoordinator`, `AppTabController`, and the active per-tab `*Coordinator`. Screens with no reachable caller are **DEAD** and excluded here.

Only **live** screens are listed. The full audit (134 used + 123 dead = 257 total) was performed during exploration.

Legend: **[Phase]** = which delivery phase from `docs/phases.md` the screen ships in.

---

## 1. Auth & Onboarding

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 1.1 | Splash — Android: `WelcomeScreen` (the iOS "Splash" is the welcome/onboarding screen; Android has no separate splash screen) | `SwiftUI/OnBoarding/Splash/SplashScreen.swift` | P1 |
| 1.2 | Login | `SwiftUI/OnBoarding/Login/LoginView.swift` | P1 |
| 1.3 | Signup — full name | `SwiftUI/OnBoarding/Signup/Views/SignupFullNameView.swift` | P1 |
| 1.4 | Signup — nickname | `SwiftUI/OnBoarding/Signup/Views/SignupNicknameView.swift` | P1 |
| 1.5 | Signup — email | `SwiftUI/OnBoarding/Signup/Views/SignupEmailView.swift` | P1 |
| 1.6 | Signup — password | `SwiftUI/OnBoarding/Signup/Views/SignupPasswordView.swift` | P1 |
| 1.7 | Signup — phone | `SwiftUI/OnBoarding/Signup/Views/SignupPhoneView.swift` | P1 |
| 1.8 | Signup — country code picker | `SwiftUI/OnBoarding/Signup/Views/SignupCountryCodeView.swift` | P1 |
| 1.9 | Signup — language | `SwiftUI/OnBoarding/Signup/Views/SignupLanguageView.swift` | P1 |
| 1.10 | Signup — Kornerz interests | `SwiftUI/OnBoarding/Signup/Views/SignupKornerzView.swift` | P1 |
| 1.11 | Signup — photo | `SwiftUI/OnBoarding/Signup/Views/SignupPhotoView.swift` | P1 |
| 1.12 | Signup — privacy policy (inline) | `SwiftUI/OnBoarding/Signup/Views/PrivacyPolicyView.swift` | P1 |
| 1.13 | Forgot password (request) | `User/ForgotPassword/ForgotPasswordScreen.swift` | P1 |
| 1.14 | Confirm code (OTP) | `User/ConfirmCode/ConfirmCodeScreen.swift` | P1 |
| 1.15 | Change password | `User/ChangePassword/ChangePasswordScreen.swift` | P1 |
| 1.16 | Force-update sheet | `SwiftUI/ForceUpdate/AppUpdateSheet.swift` | P1 |

---

## 2. Tab Shell & Klouders

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 2.1 | Root tab bar (5 tabs) | `SwiftUI/TabView/AppTabController.swift` | P2 |
| 2.2 | Home tab host | `SwiftUI/Dashboard/HomeTab/HomeTabController.swift` | P2 |
| 2.3 | Kornerz tab host | `SwiftUI/Dashboard/Korner/KornerTab/KornerTabController.swift` | P3 |
| 2.4 | Nooks tab host | `SwiftUI/Dashboard/Nooks/NookTab/NooksTabController.swift` | P4 |
| 2.5 | Loops tab host | `SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabController.swift` | P5 |
| 2.6 | Ranking (Klouders) host | `HomeVC/Ranking/RankingViewController.swift` | P10 |
| 2.7 | Ranking (SwiftUI view) | `HomeVC/Ranking/RankingScreen.swift` | P10 |

---

## 3. Home Tab Sections

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 3.1 | Home tab root | `SwiftUI/Dashboard/HomeTab/HomeTabView.swift` | P2 |
| 3.2 | Home header (avatar, greeting) | `SwiftUI/Dashboard/HomeTab/Sections/HeaderView/HomeHeaderView.swift` | P2 |
| 3.3 | Home feed section composer | `SwiftUI/Dashboard/HomeTab/Sections/HomeFeedSection.swift` | P2 |
| 3.4 | Loop of the day | `SwiftUI/Dashboard/HomeTab/Sections/LoopSection/LoopOfDaySectionView.swift` | P2 |
| 3.5 | Recent articles | `SwiftUI/Dashboard/HomeTab/Sections/RecentArticles/RecentArticleView.swift` | P2 |
| 3.6 | Recent loops | `SwiftUI/Dashboard/HomeTab/Sections/RecentLoops/RecentLoopsView.swift` | P2 |
| 3.7 | Upcoming nooks | `SwiftUI/Dashboard/HomeTab/Sections/UpcomingNooks/UpcomingNooksView.swift` | P2 |
| 3.8 | Your Kornerz | `SwiftUI/Dashboard/HomeTab/Sections/YourKornerz/YourKornerzSectionView.swift` | P2 |
| 3.9 | Kornerz carousel | `SwiftUI/Dashboard/Korner/Sections/KornerzCarouselView.swift` | P2 |

---

## 4. Kornerz Tab

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 4.1 | Kornerz tab root | `SwiftUI/Dashboard/Korner/KornerTab/KornerzTabView.swift` | P3 |
| 4.2 | Tab header (shared) | `SwiftUI/Dashboard/Korner/Sections/TabHeaderView.swift` | P3 |
| 4.3 | Korner detail host | `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailController.swift` | P3 |
| 4.4 | Korner detail view | `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailView.swift` | P3 |
| 4.5 | Korner detail header | `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailHeaderView.swift` | P3 |
| 4.6 | Korner "About" | `SwiftUI/Dashboard/Korner/KornerAbout/KornerAboutView.swift` | P3 |
| 4.7 | Korner current nook | `SwiftUI/Dashboard/Korner/Sections/KornerCurrentNookView.swift` | P3 |
| 4.8 | Korner nooks list | `SwiftUI/Dashboard/Korner/Sections/KornerDetailNooksView.swift` | P3 |
| 4.9 | Single Korner nook tile | `SwiftUI/Dashboard/Korner/Sections/KornerNookView.swift` | P3 |
| 4.10 | Korner members | `SwiftUI/Dashboard/Korner/Sections/KornerUsersView.swift` | P3 |

---

## 5. Nooks Tab

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 5.1 | Nooks tab root | `SwiftUI/Dashboard/Nooks/NookTab/NooksTabView.swift` | P4 |
| 5.2 | Single nook card | `SwiftUI/Dashboard/Nooks/Views/NookView.swift` | P4 |
| 5.3 | Scheduled/upcoming nook | `SwiftUI/Dashboard/Nooks/Views/ScheduledNookView.swift` | P4 |

---

## 6. Loops Tab

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 6.1 | Loops tab root | `SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabView.swift` | P5 |
| 6.2 | Loops filter chips | `SwiftUI/Dashboard/Loops/LoopsTab/LoopsFilterView.swift` | P5 |
| 6.3 | Single loop item (cell) | `SwiftUI/Dashboard/Loops/LoopsTab/LoopItemView.swift` | P5 |
| 6.4 | Loop detail host | `SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailController.swift` | P5 |
| 6.5 | Loop detail view | `SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailView.swift` | P5 |
| 6.6 | Single loop comment | `SwiftUI/Dashboard/Loops/Comments/LoopCommentView.swift` | P5 |
| 6.7 | Create comment host | `SwiftUI/Dashboard/Loops/Comments/Create/CreateCommentController.swift` | P5 |
| 6.8 | Create comment view | `SwiftUI/Dashboard/Loops/Comments/Create/CreateCommentScreen.swift` | P5 |
| 6.9 | Reply thread host | `SwiftUI/Dashboard/Loops/Comments/ReplyView/LoopReplyController.swift` | P5 |
| 6.10 | Reply thread view | `SwiftUI/Dashboard/Loops/Comments/ReplyView/LoopReplyScreen.swift` | P5 |

---

## 7. Profile & Account

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 7.1 | User profile (own) | `HomeVC/ProfileVC/UserProfileVC.swift` | P6 |
| 7.2 | Edit basic info | `HomeVC/ProfileVC/EditBasicInfoVC/EditBasicInfoVC.swift` | P6 |
| 7.3 | Change basic info (single field) | `HomeVC/ProfileVC/EditBasicInfoVC/ChangeBasicInfoVC/ChangeBasicInfoVC.swift` | P6 |
| 7.4 | Change basic info success popup | `HomeVC/ProfileVC/EditBasicInfoVC/ChangeBasicInfoVC/ChangeBasicInfoSuccessVC/ChangeBasicInfoSuccessVC.swift` | P6 |
| 7.5 | Privacy policy (PDF viewer) | `HomeVC/ProfileVC/PrivacyPolicyVC/PrivacyPolicyVC.swift` | P6 |

---

## 8. Notifications

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 8.1 | Notifications host | `HomeVC/Notifications/NotificationsController.swift` | P6 |
| 8.2 | Notifications feed | `HomeVC/Notifications/NotificationScreen.swift` | P6 |

---

## 9. Create Community Wizard

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 9.1 | Create community home (orchestrator) | `HomeVC/CreateCommunity/CreateCommunityHomeVC/CreateCommunityHomeVC.swift` | P7 |
| 9.2 | Community — name | `HomeVC/CreateCommunity/NewCommunityNameVC/NewCommunityNameVC.swift` | P7 |
| 9.3 | Community — description | `HomeVC/CreateCommunity/NewCommunityDescriptionVC/NewCommunityDescriptionVC.swift` | P7 |
| 9.4 | Community — theme picker | `HomeVC/CreateCommunity/SelectCommunityThemeVC/SelectCommunityThemeVC.swift` | P7 |

---

## 10. Admin (Owned Communities)

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 10.1 | Admin community list host | `HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/AdminCommunityListController.swift` | P7 |
| 10.2 | Admin community list view | `HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/AdminCommunityListScreen.swift` | P7 |
| 10.3 | Admin community list row | `HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/CommunityItemView.swift` | P7 |

---

## 11. Premium / IAP

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 11.1 | Subscription plans host | `InAppPurchase/SubscriptionController.swift` | P8 |
| 11.2 | Subscription plans view | `InAppPurchase/SubscriptionScreen.swift` | P8 |
| 11.3 | Subscription success | `InAppPurchase/SubscriptionSuccessScreen.swift` | P8 |

---

## 12. Video Calling (Vonage)

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 12.1 | Video call view | `SwiftUI/VideoCall/VideoCallView.swift` | P9 |
| 12.2 | Video call loading state | `SwiftUI/VideoCall/VideoCallLoadingState.swift` | P9 |
| 12.3 | Publisher container | `SwiftUI/VideoCall/PublisherContainer.swift` | P9 |
| 12.4 | Subscriber container | `SwiftUI/VideoCall/SubscriberContainer.swift` | P9 |

---

## 13. Cross-cutting

| # | Screen | iOS reference | Phase |
|---|---|---|---|
| 13.1 | Deep-link interstitial | `DeepLinks/DeeplinkViewController.swift` | P10 |

---

## What is NOT in the Android port (verified unreachable in iOS)

These source files exist but are **dead code** — no active caller references them. They are intentionally **excluded** from the Android port.

| Category | Dead iOS files |
|---|---|
| Legacy tab root | `HomeVC/HomeVC.swift`, `HomeVC/AppHomeVC/AppHomeVC.swift` |
| Legacy login | `User/LoginVC/LoginVC.swift`, `PaddingTextField.swift` |
| Legacy notifications | `HomeVC/NotificationVC/NotificationVC.swift` |
| Legacy community list / posts / forums / videos list | `HomeVC/CommunitiesVC/CommunitiesVC.swift`, `PostsList/*`, `ForumsList/*`, `VideosList/*` |
| Legacy community detail chain | `HomeVC/CommunitiesVC/CommunityDetailVC/*` (parent + comments + post list + description + events) |
| Legacy admin chain | `HomeVC/CommunityDetailAsAdmin/CommunityDetailAsAdminVC.swift` + its entire subtree (event/article/video/forum creation + pending requests) |
| **Forum detail (no entry point in active flow)** | `HomeVC/ForumDetail/ForumDetailViewController.swift`, `ForumDetailScreen.swift`, `ForumDetailViewModel.swift`, `CommentView.swift` |
| **Post detail (no entry point in active flow)** | `HomeVC/CommunitiesVC/CommunityDetailVC/CommunityPostDetailVC/PostDetailController.swift`, `PostDetailScreen.swift`, `PostDetailViewModel.swift` |
| **Event detail + RSVP (no entry point in active flow)** | `HomeVC/CommunitiesVC/Events/EventDetailVC/*` (4 screens) |
| Legacy event creation wizard (12 screens) | `HomeVC/EventCreation/*` |
| Legacy video creation wizard (6 screens) | `HomeVC/VideoCreation/*` |
| Legacy article creation wizard (7 screens) | `HomeVC/ArticleCreation/*` |
| Legacy forum creation wizard (5 screens) | `HomeVC/AddForum/*` |
| Legacy carousels | `HomeVC/AppHomeVC/Carousels/*` (Forums, LatestVideos, LatestPosts, Community) |
| Legacy cells | `MostViewedVideosCell`, `MostReadArticlesCell`, `RegisterToAppCell`, `HostingTableViewCell`, `CommunitiesListingCell`, `CommunityCell`, `StartNewConversationCell`, `UserInfoCell` |
| Legacy video player | `HomeVC/AppHomeVC/MostViewedVideosCell/VideoPlayerViewController.swift`, `Helper/VideoPlayer.swift` |
| Legacy OpenTok UIKit meeting | `HomeVC/OpenTokMeeting/CommunityMeetingVC/CommunityMeetingVC.swift`, `SubscriberCollectionCell.swift` |
| Legacy forum tab | `SwiftUI/Dashboard/Loops/ForumTab/*` (4 files — defined but `forumTabController()` is never called by `AppTabController`) |
| Legacy in-app purchase | `InAppPurchase/InAppViewController.swift`, `InappSuccesViewController.swift` |
| Misc dead | `App/DBBackup.swift`, `Helper/AAImagePicker.swift`, `Components/RichTextEditController.swift`, `Components/RichTextEditorView.swift`, `SwiftUI/ActionDialog/ActionDialogCoordination.swift`, `LoggedInUserView.swift`, `RecentArticlesView.swift` (+ ViewModel), `SparkCarouselItemView.swift`, `HomeVC/CommunitiesVC/AddPostVC/*` |

---

## Summary

| Category | Live screens |
|---|---|
| Auth & Onboarding | 16 |
| Tab Shell & Klouders | 7 |
| Home Tab | 9 |
| Kornerz Tab | 10 |
| Nooks Tab | 3 |
| Loops Tab | 10 |
| Profile & Account | 5 |
| Notifications | 2 |
| Create Community | 4 |
| Admin | 3 |
| Premium / IAP | 3 |
| Video Calling | 4 |
| Cross-cutting | 1 |
| **Total live screens** | **77** |

**Dropped from initial draft** (verified dead in iOS active flow per stakeholder review, 2026-10-07):
- Forum Detail — 3 screens
- Post Detail — 2 screens
- Event Detail + RSVP — 4 screens
- **Subtotal dropped: 9 screens**

That compares with 134 audited as USED — the difference is view-model / cell / helper items counted by the audit but not actually navigable as standalone screens. **77 Android screens** is the real port target.