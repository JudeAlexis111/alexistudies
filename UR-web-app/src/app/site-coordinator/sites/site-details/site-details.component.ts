import {Component, OnInit, TemplateRef} from '@angular/core';
import {SiteParticipants} from '../shared/site-detail.model';
import {Router, ActivatedRoute} from '@angular/router';
import {BsModalService, BsModalRef} from 'ngx-bootstrap/modal';
import {ToastrService} from 'ngx-toastr';
import {BehaviorSubject, Observable, of, combineLatest} from 'rxjs';
import {SiteDetailsService} from '../shared/site-details.service';
import {RegistryParticipant} from 'src/app/shared/participant';
import {map} from 'rxjs/operators';
import {UpdateInviteResponse} from '../../participant-details/participant-details';
import {ApiResponse} from 'src/app/entity/api.response.model';
import {UnsubscribeOnDestroyAdapter} from 'src/app/unsubscribe-on-destroy-adapter';
import {getMessage} from 'src/app/shared/success.codes.enum';
import {OnboardingStatus} from 'src/app/shared/enums';
@Component({
  selector: 'app-site-details',
  templateUrl: './site-details.component.html',
  styleUrls: ['./site-details.component.scss'],
})
export class SiteDetailsComponent extends UnsubscribeOnDestroyAdapter
  implements OnInit {
  query$ = new BehaviorSubject('');
  siteParticipants$: Observable<SiteParticipants> = of();
  siteId = '';
  sendResend = '';
  enableDisable = '';
  toggleDisplay = false;
  userIds: string[] = [];
  onBoardingStatus = OnboardingStatus;
  activeTab = 'All';
  maximumUser = 10;
  constructor(
    private readonly particpantDetailService: SiteDetailsService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly toastr: ToastrService,
    private readonly modalService: BsModalService,
    public modalRef: BsModalRef,
  ) {
    super();
  }
  openModal(templateRef: TemplateRef<unknown>): void {
    this.modalRef = this.modalService.show(templateRef);
  }
  ngOnInit(): void {
    this.subs.add(
      this.route.params.subscribe((params) => {
        if (params.siteId) {
          this.siteId = params.siteId as string;
        }
        this.fetchSiteParticipant(this.onBoardingStatus.All);
      }),
    );
  }
  toggleParticipant(): void {
    this.toggleDisplay = !this.toggleDisplay;
  }
  fetchSiteParticipant(fetchingOption: OnboardingStatus): void {
    this.siteParticipants$ = combineLatest(
      this.particpantDetailService.get(this.siteId, fetchingOption),
      this.query$,
    ).pipe(
      map(([siteDetails, query]) => {
        siteDetails.participantRegistryDetail.registryParticipants = siteDetails.participantRegistryDetail.registryParticipants.filter(
          (participant: RegistryParticipant) =>
            participant.email.toLowerCase().includes(query.toLowerCase()),
        );
        return siteDetails;
      }),
    );
  }
  search(query: string): void {
    this.query$.next(query.trim());
  }
  changeTab(tab: OnboardingStatus): void {
    this.sendResend =
      tab === this.onBoardingStatus.New
        ? 'Send Invitation'
        : 'Resend Invitation';
    this.enableDisable =
      tab === this.onBoardingStatus.New || tab === this.onBoardingStatus.Invited
        ? 'Disable Invitation'
        : 'Enable Invitation';
    this.activeTab = tab;
    this.toggleDisplay = false;
    this.userIds = [];
    this.fetchSiteParticipant(tab);
  }
  redirectParticipant(userId: string): void {
    void this.router.navigate(['/user/participantDetail', userId]);
  }
  rowCheckBoxChange(event: Event): void {
    const checkbox = event.target as HTMLInputElement;
    if (checkbox.checked) {
      this.userIds.push(checkbox.id);
    } else {
      this.userIds = this.userIds.filter((item) => item !== checkbox.id);
    }
  }
  decommissionSite(): void {
    this.subs.add(
      this.particpantDetailService
        .siteDecommission(this.siteId)
        .subscribe((successResponse: ApiResponse) => {
          if (getMessage(successResponse.code)) {
            this.toastr.success(getMessage(successResponse.code));
          } else {
            this.toastr.success(successResponse.message);
            void this.router.navigate(['/sites']);
          }
        }),
    );
  }
  sendInvitation(): void {
    if (this.userIds.length > 0) {
      if (this.userIds.length > 11) {
        this.toastr.error('Please select less than 10 participants');
      } else {
        const sendInvitations = {
          ids: this.userIds,
        };
        this.subs.add(
          this.particpantDetailService
            .sendInvitation(this.siteId, sendInvitations)
            .subscribe((successResponse: UpdateInviteResponse) => {
              if (getMessage(successResponse.code)) {
                this.toastr.success(getMessage(successResponse.code));
              } else {
                this.toastr.success(successResponse.message);
                this.changeTab(this.onBoardingStatus.Invited);
              }
            }),
        );
      }
    } else {
      this.toastr.error('Please select at least one participant');
    }
  }
  toggleInvitation(): void {
    if (this.userIds.length > 0) {
      if (this.userIds.length > this.maximumUser) {
        this.toastr.error('Please select less than 10 participants');
      } else {
        const statusUpdate = this.activeTab === 'Disabled' ? 'N' : 'D';
        const invitationUpdate = {
          ids: this.userIds,
          status: statusUpdate,
        };
        this.subs.add(
          this.particpantDetailService
            .toggleInvitation(this.siteId, invitationUpdate)
            .subscribe((successResponse: ApiResponse) => {
              if (getMessage(successResponse.code)) {
                this.toastr.success(getMessage(successResponse.code));
              } else {
                this.toastr.success(successResponse.message);
                this.changeTab(
                  this.activeTab === this.onBoardingStatus.Disabled
                    ? this.onBoardingStatus.Disabled
                    : this.onBoardingStatus.New,
                );
              }
            }),
        );
      }
    } else {
      this.toastr.error('Please select at least one participant');
    }
  }
  onSucceedAddEmail(): void {
    this.modalRef.hide();
    this.fetchSiteParticipant(this.onBoardingStatus.New);
  }
  onFileImportSuccess(): void {
    this.fetchSiteParticipant(this.onBoardingStatus.New);
    this.modalRef.hide();
  }
}
