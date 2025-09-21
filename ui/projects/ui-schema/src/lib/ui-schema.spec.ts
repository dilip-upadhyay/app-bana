import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UiSchema } from './ui-schema';

describe('UiSchema', () => {
  let component: UiSchema;
  let fixture: ComponentFixture<UiSchema>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UiSchema]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UiSchema);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
